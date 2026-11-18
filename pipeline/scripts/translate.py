"""Translate a GMT file to other identifier types (Aim 2.1: the adopted mapping method).

Same source of truth as the legacy GenesetTools translate (the UniProt ID-mapping service, restricted
to the species), plus the fixes the benchmark called for:

  * version pinning  - the UniProt release (X-UniProt-Release header) and date are written to
                       <out>.manifest.json, and the full ID mapping table to <out>.mapping.tsv,
                       so a release can be rebuilt and two months compared
  * renamed genes    - (human symbols) a symbol that is an unambiguous HGNC *previous* symbol is
                       replaced by the current symbol before mapping ("rescued")
  * ambiguity        - a symbol that is an alias/previous symbol of 2+ current genes is NOT silently
                       resolved: it is left out and listed with status "ambiguous". If a symbol maps
                       to 2+ genes, HGNC's own ID for that symbol is used when it is one of them;
                       otherwise it is flagged ambiguous and left out.
  * one protein, several genes - a UniProt accession that maps to 2+ genes (duplicated genes such
                       as DEFB4A/DEFB4B) keeps all of them (status "multi_kept"); the legacy code kept
                       one at random
  * incomplete jobs  - IDs missing from a "finished" UniProt job are asked for again once; the number
                       recovered is in the manifest (recovered_on_reask)
  * symbol lookups   - go through reviewed (Swiss-Prot) entries only, which removes most fragments
                       and unreviewed duplicates

Usage:
  translate.py --gmt Human_GO_bp_no_GO_iea_UniProt.gmt --from uniprot --to entrezgene symbol ensembl \
               --taxid 9606 --hgnc raw/reference/hgnc_complete_set.txt --out build/.../Human_GO_bp_no_GO_iea
writes <out>_<target>.gmt, <out>.mapping.tsv, <out>.manifest.json
"""
import argparse
import json
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd
import requests

from common import USER_AGENT, read_gmt, sha256

API = "https://rest.uniprot.org/idmapping"
S = requests.Session()
S.headers["User-Agent"] = USER_AGENT
FROM = {"uniprot": "UniProtKB_AC-ID", "entrezgene": "GeneID", "symbol": "Gene_Name", "ensembl": "Ensembl"}
TO = {"uniprot": "UniProtKB", "entrezgene": "GeneID", "symbol": "Gene_Name", "ensembl": "Ensembl"}
# targets where one gene legitimately has several IDs (isoform accessions, alt-haplotype genes)
MULTI_OK = {"uniprot", "ensembl"}
RELEASE = {}
RECOVERED = {}  # IDs that only mapped on the second ask, per query type


# ------------------------------------------------------------------ UniProt
def _job(ids, frm, to, taxid=None):
    data = {"from": frm, "to": to, "ids": ",".join(ids)}
    if taxid and frm in ("Gene_Name", "GeneID", "Ensembl"):
        data["taxId"] = str(taxid)
    r = S.post(f"{API}/run", data=data, timeout=120)
    r.raise_for_status()
    RELEASE.setdefault("uniprot_release", r.headers.get("X-UniProt-Release"))
    RELEASE.setdefault("uniprot_release_date", r.headers.get("X-UniProt-Release-Date"))
    job = r.json()["jobId"]
    for _ in range(240):  # up to ~12 minutes
        st = S.get(f"{API}/status/{job}", timeout=60, allow_redirects=False)
        js = st.json() if "json" in st.headers.get("content-type", "") else {}
        if st.status_code == 303 or "results" in js or js.get("jobStatus") == "FINISHED":
            break
        if js.get("jobStatus") in ("ERROR", "FAILURE") or js.get("messages"):
            raise RuntimeError(f"UniProt job {job} failed: {js}")
        time.sleep(3)
    else:
        raise RuntimeError(f"UniProt job {job} did not finish")
    out = defaultdict(set)
    if to.startswith("UniProtKB"):
        url = f"{API}/uniprotkb/results/stream/{job}?format=tsv&fields=accession"
        lines = S.get(url, timeout=600).text.splitlines()[1:]
        for line in lines:
            f, acc = line.split("\t")[:2]
            out[f].add(acc)
    else:
        r = S.get(f"{API}/stream/{job}", timeout=600)
        r.raise_for_status()
        for row in r.json().get("results", []):
            v = str(row["to"])
            out[row["from"]].add(v.split(".")[0] if to == "Ensembl" else v)
    return out


def _job_retry(ids, frm, to, taxid):
    for attempt in range(3):  # UniProt jobs sometimes fail on their side; resubmit
        try:
            return _job(ids, frm, to, taxid)
        except (RuntimeError, requests.RequestException) as e:
            if attempt == 2:
                raise
            print(f"retrying after: {e}")
            time.sleep(10 * (attempt + 1))


def uniprot_map(ids, frm, to, taxid, batch=5000):
    out = defaultdict(set)
    ids = sorted(set(ids))
    for i in range(0, len(ids), batch):
        chunk = ids[i:i + batch]
        res = _job_retry(chunk, frm, to, taxid)
        # UniProt can report a job FINISHED with only part of the results (seen 2026-11-18: 1,598 of
        # 2,618 IDs missing, all mapped fine on a fresh job). Ask again once for anything that came back empty.
        missing = [x for x in chunk if x not in res]
        if missing:
            again = _job_retry(missing, frm, to, taxid)
            RECOVERED[f"{frm}->{to}"] = RECOVERED.get(f"{frm}->{to}", 0) + len(again)
            res.update(again)
        for k, v in res.items():
            out[k] |= v
    return out


# ------------------------------------------------------------------ HGNC symbol clean-up
def load_hgnc(path):
    h = pd.read_csv(path, sep="\t", dtype=str, low_memory=False).fillna("")
    return h[h["status"] == "Approved"]


def symbol_resolver(hgnc):
    approved = set(hgnc["symbol"])
    prev, alias = defaultdict(set), defaultdict(set)
    for s, ps, al in zip(hgnc["symbol"], hgnc["prev_symbol"], hgnc["alias_symbol"]):
        for p in filter(None, ps.split("|")):
            prev[p].add(s)
        for a in filter(None, al.split("|")):
            alias[a].add(s)

    def resolve(sym):
        if sym in approved:
            return sym, "approved"
        cands = prev.get(sym, set()) | alias.get(sym, set())
        if len(cands) == 1:
            return next(iter(cands)), "rescued_previous" if sym in prev else "rescued_alias"
        if len(cands) > 1:
            return None, "ambiguous"
        return sym, "unknown_to_hgnc"  # still try UniProt (e.g. non-human)
    return resolve


# ------------------------------------------------------------------ core
def map_ids(ids, frm, targets, taxid, hgnc=None):
    """Return (mapping {target: {native: set}}, status {native: str}, notes {native: str})."""
    ids = sorted(set(ids))
    status, notes, query = {}, {}, {}
    official = {}
    if hgnc is not None:
        for sym, ent, ens in zip(hgnc["symbol"], hgnc["entrez_id"], hgnc["ensembl_gene_id"]):
            official[sym] = {"entrezgene": ent, "ensembl": ens, "symbol": sym}
    if frm == "symbol" and hgnc is not None and str(taxid) == "9606":
        resolve = symbol_resolver(hgnc)
        for i in ids:
            new, st = resolve(i)
            status[i] = st
            if new is None:
                continue
            query[i] = new
            if new != i:
                notes[i] = f"{i}->{new}"
    else:
        query = {i: i for i in ids}

    qids = sorted(set(query.values()))
    if frm == "uniprot":
        base = {q: {q} for q in qids}
    else:  # go through reviewed UniProt entries of this species
        base = uniprot_map(qids, FROM[frm], "UniProtKB-Swiss-Prot", taxid)
    accs = sorted(set().union(*base.values())) if base else []

    mapping = {}
    for t in targets:
        if t == "uniprot":
            acc_t = {a: {a} for a in accs}
        else:
            acc_t = uniprot_map(accs, "UniProtKB_AC-ID", TO[t], taxid)
        m = {}
        for orig, q in query.items():
            got = set().union(*(acc_t.get(a, set()) for a in base.get(q, ()))) if base.get(q) else set()
            if len(got) > 1 and t not in MULTI_OK:
                if frm == "uniprot":
                    status.setdefault(orig, "multi_kept")
                elif official.get(q, {}).get(t) in got:
                    got = {official[q][t]}
                else:
                    status.setdefault(orig, "ambiguous")
                    notes[orig] = (notes.get(orig, "") + f" {t}:" + "|".join(sorted(got))).strip()
                    continue
            if got:
                m[orig] = got
        mapping[t] = m
    for i in ids:
        status.setdefault(i, "ok" if any(i in m for m in mapping.values()) else "unmapped")
        if status[i] in ("approved", "unknown_to_hgnc"):
            status[i] = "ok" if any(i in m for m in mapping.values()) else "unmapped"
    return mapping, status, notes


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--gmt", required=True)
    ap.add_argument("--from", dest="frm", required=True, choices=list(FROM))
    ap.add_argument("--to", nargs="+", required=True, choices=list(TO))
    ap.add_argument("--taxid", required=True)
    ap.add_argument("--hgnc", help="HGNC complete set (human symbol clean-up)")
    ap.add_argument("--out", required=True, help="output prefix")
    a = ap.parse_args()

    sets = list(read_gmt(a.gmt))
    ids = {g for _, _, gs in sets for g in gs}
    if not ids:
        raise SystemExit(f"{a.gmt}: no genes - refusing to translate an empty file")
    hgnc = load_hgnc(a.hgnc) if a.hgnc else None
    mapping, status, notes = map_ids(ids, a.frm, a.to, a.taxid, hgnc)

    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    outputs, per_target = {}, {}
    for t, m in mapping.items():
        path = Path(f"{a.out}_{t}.gmt")
        kept = empty = 0
        with open(path, "w") as fh:
            for name, desc, genes in sets:
                new = sorted({x for g in genes for x in m.get(g, ())})
                if not new:
                    empty += 1
                    continue
                fh.write(f"{name}\t{desc}\t" + "\t".join(new) + "\n")
                kept += 1
        if kept == 0:
            raise SystemExit(f"translation to {t} produced no gene sets - UniProt problem?")
        outputs[path.name] = sha256(path)
        per_target[t] = {"ids_mapped": len(m), "ids_total": len(ids),
                         "pct_mapped": round(100 * len(m) / len(ids), 2),
                         "gene_sets_written": kept, "gene_sets_dropped_empty": empty}

    with open(f"{a.out}.mapping.tsv", "w") as fh:
        fh.write("native_id\tstatus\tnote\t" + "\t".join(mapping) + "\n")
        for i in sorted(ids):
            fh.write(f"{i}\t{status[i]}\t{notes.get(i, '')}\t" +
                     "\t".join("|".join(sorted(mapping[t].get(i, ()))) for t in mapping) + "\n")

    counts = defaultdict(int)
    for s in status.values():
        counts[s] += 1
    manifest = {
        "input": {"file": Path(a.gmt).name, "sha256": sha256(a.gmt), "id_type": a.frm, "taxid": a.taxid},
        "method": "UniProt ID mapping REST" + (" + HGNC previous/alias resolution" if hgnc is not None else ""),
        "uniprot_release": RELEASE.get("uniprot_release"),
        "uniprot_release_date": RELEASE.get("uniprot_release_date"),
        "hgnc_file_sha256": sha256(a.hgnc) if a.hgnc else None,
        "run_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "id_status_counts": dict(counts),
        "recovered_on_reask": RECOVERED,
        "targets": per_target,
        "outputs": outputs,
        "mapping_table": Path(f"{a.out}.mapping.tsv").name,
    }
    Path(f"{a.out}.manifest.json").write_text(json.dumps(manifest, indent=2))
    print(json.dumps({k: manifest[k] for k in ("uniprot_release", "id_status_counts", "targets")}, indent=1))


if __name__ == "__main__":
    main()
