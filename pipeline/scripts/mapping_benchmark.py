"""Aim 2.1: identifier mapping benchmark.

Two tests, both mapping to Entrez Gene IDs (the ID the GMT files and orthology conversion use):

A. Coverage on real data: every UniProt accession in the human GO GAF.
   Truth = HGNC approved genes that list that accession (only where HGNC has one).
B. Labelled symbol test set (~200 symbols, kept as a regression test):
     unambiguous  - approved symbol, not anyone's alias/previous symbol     -> its Entrez ID
     renamed      - previous symbol of exactly one current gene             -> that gene's Entrez ID
     shared_alias - alias used by 2+ current genes                          -> correct = NOT a single confident answer
     withdrawn    - withdrawn HGNC symbol, not reused                       -> correct = unmapped

Methods:
  legacy    UniProt ID mapping REST (what GenesetTools translate does: UniProt->GeneID;
            symbol->UniProtKB(taxId 9606)->GeneID)
  bridgedb  BridgeDb web service, xrefsBatch
  ensembl   Ensembl release files (uniprot.tsv + entrez.tsv); BioMart was unreachable. UniProt test only.
  gprofiler g:Profiler g:Convert REST

Responses are cached under --cache so reruns don't hit the services again.
"""
import argparse
import csv
import gzip
import json
import random
import time
from collections import defaultdict
from pathlib import Path

import pandas as pd
import requests

from common import USER_AGENT
from plotstyle import SERIES, TEXT2, plt

S = requests.Session()
S.headers["User-Agent"] = USER_AGENT
METHODS = ["legacy", "bridgedb", "ensembl", "gprofiler"]


# ------------------------------------------------------------------ cache
def cached(cache, name, fn):
    path = Path(cache) / f"{name}.json"
    if path.exists():
        return {k: set(v) for k, v in json.loads(path.read_text()).items()}
    out = fn()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({k: sorted(v) for k, v in out.items()}))
    return out


def chunks(xs, n):
    xs = list(xs)
    for i in range(0, len(xs), n):
        yield xs[i:i + n]


# ------------------------------------------------------------------ UniProt (legacy)
def uniprot_job(ids, frm, to, tax=None):
    data = {"from": frm, "to": to, "ids": ",".join(ids)}
    if tax:
        data["taxId"] = tax
    job = S.post("https://rest.uniprot.org/idmapping/run", data=data, timeout=120).json()["jobId"]
    for _ in range(200):
        st = S.get(f"https://rest.uniprot.org/idmapping/status/{job}", timeout=60, allow_redirects=False)
        js = st.json() if st.headers.get("content-type", "").startswith("application/json") else {}
        if st.status_code == 303 or "results" in js or js.get("jobStatus") == "FINISHED":
            break
        if js.get("jobStatus") in ("ERROR", "FAILURE"):
            raise RuntimeError(f"UniProt job {job}: {js}")
        time.sleep(3)
    if to == "UniProtKB":
        url = f"https://rest.uniprot.org/idmapping/uniprotkb/results/stream/{job}?format=tsv&fields=accession"
        out = defaultdict(set)
        for line in S.get(url, timeout=300).text.splitlines()[1:]:
            f, acc = line.split("\t")[:2]
            out[f].add(acc)
        return out
    js = S.get(f"https://rest.uniprot.org/idmapping/stream/{job}", timeout=300).json()
    out = defaultdict(set)
    for r in js.get("results", []):
        out[r["from"]].add(str(r["to"]))
    return out


def legacy_uniprot(accs):
    out = defaultdict(set)
    for c in chunks(accs, 5000):
        for k, v in uniprot_job(c, "UniProtKB_AC-ID", "GeneID").items():
            out[k] |= v
    return out


def legacy_symbol(symbols):
    sym2acc = uniprot_job(list(symbols), "Gene_Name", "UniProtKB", tax="9606")
    accs = sorted(set().union(*sym2acc.values())) if sym2acc else []
    acc2gene = legacy_uniprot(accs)
    return {s: set().union(*(acc2gene.get(a, set()) for a in accs_)) for s, accs_ in sym2acc.items()}


# ------------------------------------------------------------------ BridgeDb
def bridgedb(ids, code):
    out = defaultdict(set)
    for c in chunks(ids, 200):
        r = S.post(f"https://webservice.bridgedb.org/Human/xrefsBatch/{code}", data="\n".join(c),
                   headers={"Content-Type": "text/plain"}, timeout=300)
        r.raise_for_status()
        for line in r.text.splitlines():
            parts = line.split("\t")
            if len(parts) < 3:
                continue
            for x in parts[2].split(","):
                if x.startswith("L:"):
                    out[parts[0]].add(x[2:])
    return out


# ------------------------------------------------------------------ g:Profiler
def gprofiler(ids):
    out = defaultdict(set)
    for c in chunks(ids, 2000):
        r = S.post("https://biit.cs.ut.ee/gprofiler/api/convert/convert/", timeout=300,
                   json={"organism": "hsapiens", "target": "ENTREZGENE_ACC", "query": c})
        r.raise_for_status()
        for row in r.json()["result"]:
            if row["converted"] not in (None, "None", "N/A"):
                out[row["incoming"]].add(str(row["converted"]))
    return out


# ------------------------------------------------------------------ Ensembl files
def ensembl_uniprot(uniprot_tsv, entrez_tsv, accs):
    gene_entrez = defaultdict(set)
    with gzip.open(entrez_tsv, "rt") as fh:
        for r in csv.DictReader(fh, delimiter="\t"):
            gene_entrez[r["gene_stable_id"]].add(r["xref"])
    acc_gene = defaultdict(set)
    with gzip.open(uniprot_tsv, "rt") as fh:
        for r in csv.DictReader(fh, delimiter="\t"):
            acc_gene[r["xref"].split("-")[0]].add(r["gene_stable_id"])
    accs = set(accs)
    return {a: set().union(*(gene_entrez[g] for g in gs)) for a, gs in acc_gene.items() if a in accs}


# ------------------------------------------------------------------ truth + test set
def load_hgnc(path):
    h = pd.read_csv(path, sep="\t", dtype=str, low_memory=False).fillna("")
    return h[h["status"] == "Approved"]


def split(v):
    return [x for x in v.split("|") if x]


def uniprot_truth(h):
    t = defaultdict(set)
    for _, r in h.iterrows():
        if r["entrez_id"]:
            for a in split(r["uniprot_ids"]):
                t[a].add(r["entrez_id"])
    return t


def symbol_testset(h, withdrawn_path, n=50, seed=1):
    approved = set(h["symbol"])
    alias_of, prev_of = defaultdict(set), defaultdict(set)
    for _, r in h.iterrows():
        for a in split(r["alias_symbol"]):
            alias_of[a].add(r["symbol"])
        for p in split(r["prev_symbol"]):
            prev_of[p].add(r["symbol"])
    entrez = dict(zip(h["symbol"], h["entrez_id"]))
    pc = set(h.loc[h["locus_group"] == "protein-coding gene", "symbol"])
    rng = random.Random(seed)
    rows = []
    unamb = sorted(s for s in pc if entrez.get(s) and s not in alias_of and s not in prev_of)
    rows += [(s, "unambiguous", entrez[s]) for s in rng.sample(unamb, n)]
    ren = sorted(p for p, gs in prev_of.items() if len(gs) == 1 and p not in approved and p not in alias_of
                 and next(iter(gs)) in pc and entrez.get(next(iter(gs))))
    rows += [(p, "renamed", entrez[next(iter(prev_of[p]))]) for p in rng.sample(ren, n)]
    shared = sorted(a for a, gs in alias_of.items() if len(gs) >= 2 and a not in approved and a not in prev_of)
    rows += [(a, "shared_alias", "AMBIGUOUS:" + "|".join(sorted(entrez.get(g, "") for g in alias_of[a])))
             for a in rng.sample(shared, n)]
    w = pd.read_csv(withdrawn_path, sep="\t", dtype=str).fillna("")
    w = w[w["STATUS"] == "Entry Withdrawn"]["WITHDRAWN_SYMBOL"]
    wd = sorted(s for s in set(w) if s not in approved and s not in alias_of and s not in prev_of)
    rows += [(s, "withdrawn", "NONE") for s in rng.sample(wd, n)]
    return pd.DataFrame(rows, columns=["query", "category", "truth"])


def score_symbol(row, got):
    cat, truth = row["category"], row["truth"]
    if cat in ("unambiguous", "renamed"):
        return got == {truth}
    if cat == "shared_alias":
        return len(got) != 1  # unmapped or all candidates = honest; exactly one = silent guess
    return len(got) == 0


# ------------------------------------------------------------------ main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--hgnc", required=True)
    ap.add_argument("--withdrawn", required=True)
    ap.add_argument("--ens-entrez", required=True)
    ap.add_argument("--ens-uniprot", required=True)
    ap.add_argument("--gaf", required=True)
    ap.add_argument("--cache", required=True)
    ap.add_argument("--outdir", required=True)
    a = ap.parse_args()
    out = Path(a.outdir)
    out.mkdir(parents=True, exist_ok=True)

    h = load_hgnc(a.hgnc)
    truth = uniprot_truth(h)
    accs = set()
    with gzip.open(a.gaf, "rt") as fh:
        for line in fh:
            if not line.startswith("!"):
                t = line.split("\t")
                if len(t) > 1 and t[0] == "UniProtKB":
                    accs.add(t[1].split("-")[0])
    accs = sorted(accs)

    calls = {
        "legacy": cached(a.cache, "uniprot_legacy", lambda: legacy_uniprot(accs)),
        "bridgedb": cached(a.cache, "uniprot_bridgedb", lambda: bridgedb(accs, "S")),
        "ensembl": ensembl_uniprot(a.ens_uniprot, a.ens_entrez, accs),
        "gprofiler": cached(a.cache, "uniprot_gprofiler", lambda: gprofiler(accs)),
    }
    rows = []
    for m in METHODS:
        c = calls[m]
        with_truth = [x for x in accs if x in truth]
        mapped = [x for x in accs if c.get(x)]
        rows.append({
            "test": "A_uniprot_GO", "method": m, "n_query": len(accs),
            "coverage_pct": round(100 * len(mapped) / len(accs), 2),
            "one_to_many_pct": round(100 * sum(len(c[x]) > 1 for x in mapped) / max(len(mapped), 1), 2),
            "n_with_truth": len(with_truth),
            "accuracy_pct": round(100 * sum(c.get(x, set()) == truth[x] for x in with_truth) / len(with_truth), 2),
            "missed_pct": round(100 * sum(not c.get(x) for x in with_truth) / len(with_truth), 2),
        })

    ts_path = out / "symbol_testset.tsv"
    ts = pd.read_csv(ts_path, sep="\t", dtype=str) if ts_path.exists() else symbol_testset(h, a.withdrawn)
    ts.to_csv(ts_path, sep="\t", index=False)
    q = list(ts["query"])
    scalls = {
        "legacy": cached(a.cache, "symbol_legacy", lambda: legacy_symbol(q)),
        "bridgedb": cached(a.cache, "symbol_bridgedb", lambda: bridgedb(q, "H")),
        "gprofiler": cached(a.cache, "symbol_gprofiler", lambda: gprofiler(q)),
    }
    detail = []
    for m, c in scalls.items():
        for _, r in ts.iterrows():
            got = c.get(r["query"], set())
            detail.append({"method": m, "query": r["query"], "category": r["category"], "truth": r["truth"],
                           "returned": "|".join(sorted(got)), "correct": score_symbol(r, got)})
    det = pd.DataFrame(detail)
    det.to_csv(out / "symbol_calls.tsv", sep="\t", index=False)
    for (m, cat), g in det.groupby(["method", "category"]):
        rows.append({"test": f"B_symbol_{cat}", "method": m, "n_query": len(g),
                     "accuracy_pct": round(100 * g["correct"].mean(), 1)})
    summ = pd.DataFrame(rows)
    summ.to_csv(out / "mapping_summary.tsv", sep="\t", index=False)
    print(summ.to_string(index=False))

    # ---- figures ----
    A = summ[summ["test"] == "A_uniprot_GO"].set_index("method").loc[METHODS]
    fig, axes = plt.subplots(1, 3, figsize=(12, 3.4), sharey=True)
    for ax, col, title in zip(axes, ["coverage_pct", "accuracy_pct", "one_to_many_pct"],
                              ["Coverage (any Entrez ID)", "Agrees with HGNC", "One-to-many"]):
        ax.barh(A.index, A[col], color=SERIES[0], height=0.55)
        for y, v in enumerate(A[col]):
            ax.annotate(f"{v:.1f}%", (v, y), xytext=(4, 0), textcoords="offset points", va="center",
                        fontsize=8, color=TEXT2)
        ax.set_title(title)
        ax.set_xlim(0, 115)
        ax.grid(axis="y", visible=False)
    axes[0].invert_yaxis()
    fig.suptitle(f"UniProt → Entrez for {len(accs):,} accessions in the human GO GAF", color=TEXT2, fontsize=9, y=0.02)
    fig.savefig(out / "mapping_uniprot.png")
    plt.close(fig)

    B = summ[summ["test"].str.startswith("B_")].copy()
    B["category"] = B["test"].str.replace("B_symbol_", "")
    cats = ["unambiguous", "renamed", "shared_alias", "withdrawn"]
    meths = ["legacy", "bridgedb", "gprofiler"]
    fig, ax = plt.subplots(figsize=(8, 3.6))
    w = 0.26
    for i, m in enumerate(meths):
        vals = [B[(B["method"] == m) & (B["category"] == c)]["accuracy_pct"].squeeze() for c in cats]
        xs = [k + (i - 1) * w for k in range(len(cats))]
        ax.bar(xs, vals, width=w - 0.03, color=SERIES[i], label=m)
        for x, v in zip(xs, vals):
            ax.annotate(f"{v:.0f}", (x, v), xytext=(0, 2), textcoords="offset points", ha="center",
                        fontsize=7, color=TEXT2)
    ax.set_xticks(range(len(cats)), ["Unambiguous", "Renamed\n(previous symbol)", "Shared alias\n(should not guess)",
                                     "Withdrawn\n(should not map)"])
    ax.set_ylabel("% correct")
    ax.set_ylim(0, 110)
    ax.set_title("Symbol → Entrez on the labelled test set (50 per category)")
    ax.legend(ncol=3, fontsize=8, loc="upper center", bbox_to_anchor=(0.5, -0.22))
    ax.grid(axis="x", visible=False)
    fig.savefig(out / "mapping_symbol_testset.png")
    plt.close(fig)


if __name__ == "__main__":
    main()
