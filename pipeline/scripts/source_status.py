"""Aim 2.3: source life cycle (active / static / unavailable) + identifier decay of static sources.

Classification (run monthly, appends to --ledger):
  unavailable  download failed in the last 2 recorded months
  static       content hash unchanged for 12 recorded months (or registered as static in sources.yaml)
  active       everything else
A maintainer confirms any change of class: rows where the class differs from sources.yaml get
needs_review = yes.

Decay: for each static source we can read, the share of its genes that no longer map to an approved
HGNC identifier in each monthly HGNC snapshot. Symbols rescued through an unambiguous previous symbol
are reported separately.
"""
import argparse
import datetime
import gzip
import hashlib
import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path

import pandas as pd
import yaml

from common import download, read_gmt
from plotstyle import SERIES, TEXT2, label_end, plt

HGNC_MONTHLY = "https://storage.googleapis.com/public-download-files/hgnc/archive/archive/monthly/tsv/hgnc_complete_set_{m}.txt"


# ------------------------------------------------------------------ gene extraction per static source
def read_bytes(repo, rel):
    p = Path(repo) / rel
    if p.exists():
        return p.read_bytes()
    try:  # file deleted in the working tree but still committed
        return subprocess.run(["git", "-C", str(repo), "show", f"HEAD:{rel}"], check=True,
                              capture_output=True).stdout
    except subprocess.CalledProcessError:
        return None


def genes_kegg(repo, cfg):
    data = read_bytes(repo, cfg["path"])
    if data is None:
        return None
    tmp = Path("/tmp") / "kegg_static.gmt"
    tmp.write_bytes(data)
    return "symbol", {g for _, _, gs in read_gmt(tmp) for g in gs}


def genes_iob(repo, cfg):
    d = Path(repo) / cfg["path"]
    ids = set()
    for f in d.glob("*.owl"):
        ids |= set(re.findall(r'RelationshipXref rdf:about="Entrez_[^"]*">\s*<bp:id[^>]*>(\d+)<', f.read_text(errors="replace")))
    return ("entrez", ids) if ids else None


def genes_nci(repo, cfg):
    data = read_bytes(repo, cfg["path"] + "/NCI-Nature_Curated.bp3.owl.gz")
    if data is None:
        return None
    txt = gzip.decompress(data).decode(errors="replace")
    ids = set(re.findall(r'<bp:db[^>]*>UniProt</bp:db>\s*<bp:id[^>]*>([A-Z0-9]+)(?:-\d+)?</bp:id>', txt))
    return ("uniprot", ids) if ids else None


EXTRACTORS = {"KEGG": genes_kegg, "IOB": genes_iob, "NCI_Nature": genes_nci}


# ------------------------------------------------------------------ decay
def available_snapshots():
    url = ("https://storage.googleapis.com/storage/v1/b/public-download-files/o?maxResults=1000&fields=items(name)"
           "&prefix=hgnc/archive/archive/monthly/tsv/hgnc_complete_set_")
    import requests
    names = [i["name"] for i in requests.get(url, timeout=60).json().get("items", [])]
    return sorted({m.group(1) for n in names if (m := re.search(r"(\d{4}-\d{2}-\d{2})\.txt$", n))})


def nearest(target, available):
    t = datetime.date.fromisoformat(target)
    return min(available, key=lambda d: abs((datetime.date.fromisoformat(d) - t).days))


def hgnc_snapshot(month, cache):
    dest = Path(cache) / f"hgnc_{month}.txt"
    if not dest.exists():
        download(HGNC_MONTHLY.format(m=month), dest)
    h = pd.read_csv(dest, sep="\t", dtype=str, low_memory=False).fillna("")
    return h[h["status"] == "Approved"]


def decay(idtype, genes, h):
    if idtype == "symbol":
        current = set(h["symbol"])
        prev = defaultdict(set)
        for s, ps in zip(h["symbol"], h["prev_symbol"]):
            for p in filter(None, ps.split("|")):
                prev[p].add(s)
        lost = {g for g in genes if g not in current}
        rescued = {g for g in lost if len(prev.get(g, ())) == 1}
        return len(lost), len(rescued)
    if idtype == "entrez":
        current = set(h["entrez_id"])
    else:
        current = {u for us in h["uniprot_ids"] for u in us.split("|") if u}
    return len({g for g in genes if g not in current}), 0


# ------------------------------------------------------------------ classification
def content_hash(repo, cfg, raw_root, month, source):
    if cfg["status"] == "static":
        p = Path(repo) / cfg["path"]
        files = sorted(p.rglob("*")) if p.is_dir() else [p]
        h = hashlib.sha256()
        found = False
        for f in files:
            if f.is_file() and f.name != ".DS_Store":
                h.update(f.read_bytes())
                found = True
        return ("ok", h.hexdigest()) if found else ("missing_local_file", "")
    metas = sorted((Path(raw_root) / source / month).glob("*.download.json"))
    if not metas:
        return ("not_run", "")
    return ("ok", hashlib.sha256("".join(json.loads(m.read_text())["sha256"] for m in metas).encode()).hexdigest())


def classify(hist, registered):
    h = hist.sort_values("month")
    last2 = h.tail(2)
    if len(last2) == 2 and (last2["outcome"] != "ok").all() and (last2["outcome"] != "not_run").all():
        return "unavailable"
    ok = h[h["outcome"] == "ok"].tail(12)
    if registered == "static" or (len(ok) == 12 and ok["sha256"].nunique() == 1):
        return "static"
    return "active"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sources", required=True)
    ap.add_argument("--repo", required=True)
    ap.add_argument("--months", nargs="+", required=True)
    ap.add_argument("--cache", required=True)
    ap.add_argument("--ledger", required=True)
    ap.add_argument("--raw", default="raw")
    ap.add_argument("--outdir", required=True)
    a = ap.parse_args()
    out = Path(a.outdir)
    out.mkdir(parents=True, exist_ok=True)
    sources = yaml.safe_load(open(a.sources))["sources"]
    month = datetime.date.today().strftime("%Y-%m")

    # ---- ledger + classification
    ledger = Path(a.ledger)
    old = pd.read_csv(ledger, sep="\t", dtype=str) if ledger.exists() else pd.DataFrame(
        columns=["month", "source", "outcome", "sha256"])
    new = []
    for s, cfg in sources.items():
        outcome, digest = content_hash(a.repo, cfg, a.raw, month, s)
        new.append({"month": month, "source": s, "outcome": outcome, "sha256": digest})
    led = pd.concat([old[old["month"] != month], pd.DataFrame(new)], ignore_index=True)
    led.to_csv(ledger, sep="\t", index=False)
    status = []
    for s, cfg in sources.items():
        cls = classify(led[led["source"] == s], cfg["status"])
        status.append({"source": s, "registered": cfg["status"], "class": cls,
                       "this_month": led[(led["source"] == s) & (led["month"] == month)]["outcome"].iloc[0],
                       "needs_review": "yes" if cls != cfg["status"] else ""})
    st = pd.DataFrame(status)
    st.to_csv(out / "source_status.tsv", sep="\t", index=False)
    print(st.to_string(index=False))

    # ---- decay of static sources
    genesets = {}
    for s, fn in EXTRACTORS.items():
        r = fn(a.repo, sources[s])
        if r:
            genesets[s] = r
    rows = []
    avail = available_snapshots()
    months = sorted({nearest(m, avail) for m in a.months})
    print("HGNC snapshots used:", " ".join(months))
    for m in months:
        h = hgnc_snapshot(m, a.cache)
        for s, (idtype, genes) in genesets.items():
            lost, rescued = decay(idtype, genes, h)
            rows.append({"month": m, "source": s, "id_type": idtype, "genes": len(genes),
                         "not_current": lost, "rescued_by_prev_symbol": rescued,
                         "decay_pct": round(100 * lost / len(genes), 2),
                         "decay_after_rescue_pct": round(100 * (lost - rescued) / len(genes), 2)})
    dec = pd.DataFrame(rows)
    dec.to_csv(out / "decay.tsv", sep="\t", index=False)
    print(dec.to_string(index=False))

    fig, ax = plt.subplots(figsize=(8, 3.8))
    dec["date"] = pd.to_datetime(dec["month"])
    for i, (s, d) in enumerate(dec.groupby("source", sort=False)):
        idt = d["id_type"].iloc[0]
        ax.plot(d["date"], d["decay_pct"], color=SERIES[i], marker="o", label=f"{s} ({idt}, {d['genes'].iloc[0]:,} genes)")
        label_end(ax, d["date"].iloc[-1], d["decay_pct"].iloc[-1], f"{d['decay_pct'].iloc[-1]:.1f}%")
        if idt == "symbol":
            ax.plot(d["date"], d["decay_after_rescue_pct"], color=SERIES[i], ls="--", marker="o",
                    label=f"{s} after previous-symbol rescue")
            label_end(ax, d["date"].iloc[-1], d["decay_after_rescue_pct"].iloc[-1],
                      f"{d['decay_after_rescue_pct'].iloc[-1]:.1f}%")
    ax.set_ylabel("% of genes not current in HGNC")
    ax.set_title("Identifier decay of static sources")
    ax.set_ylim(bottom=0)
    ax.legend(fontsize=8, loc="center left", bbox_to_anchor=(0.01, 0.62))
    fig.text(0.01, -0.02, "HGNC monthly snapshots; 'not current' = no approved gene with that ID", color=TEXT2, fontsize=8)
    fig.savefig(out / "decay.png")
    plt.close(fig)


if __name__ == "__main__":
    main()
