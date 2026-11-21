"""Aim 2.2 (steps 1-3): what did every published Bader Lab release actually contain?

Walks https://download.baderlab.org/EM_Genesets/<Month_DD_YYYY>/Human/symbol/<division>/*.gmt
(per-source files only; the combined *AllPathways* files at the top level are skipped),
and for every file and gene set source counts: lines, unique gene set IDs, duplicate IDs,
median set size, unique genes, and genes that are not approved HGNC symbols today.

* The server asks for downloads between 9 pm and 8 am Eastern only. The script refuses to start
  outside that window and stops cleanly when the window closes; rerun the next night and it resumes
  (every finished file is cached in --cache/<release>/<file>.json).
* Files are streamed and summarised, not stored.
* After collection, change points per source are found with ruptures (PELT) and plotted.
"""
import argparse
import json
import re
import statistics
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import pandas as pd
import requests

from common import USER_AGENT
from plotstyle import SERIES, TEXT2, plt

S = requests.Session()
S.headers["User-Agent"] = USER_AGENT
ET = ZoneInfo("America/Toronto")


def in_window(start=21, end=8):
    h = datetime.now(ET).hour
    return h >= start or h < end


def listing(url):
    html = S.get(url, timeout=60).text
    return [h for h in re.findall(r'href="([^"?#][^"]*)"', html) if not h.startswith(("/", "http", "..", "mailto"))]


def release_date(folder):
    try:
        return datetime.strptime(folder.strip("/"), "%B_%d_%Y").date()
    except ValueError:
        return None


def summarise(url, approved):
    per = defaultdict(lambda: {"lines": 0, "ids": [], "sizes": [], "genes": set()})
    with S.get(url, stream=True, timeout=600) as r:
        r.raise_for_status()
        for raw in r.iter_lines(decode_unicode=True):
            if not raw:
                continue
            parts = raw.rstrip("\r").split("\t")
            name = parts[0]
            bits = name.split("%")
            src, gid = (bits[-2], bits[-1]) if len(bits) >= 3 else ("UNKNOWN", name)
            genes = [g for g in parts[2:] if g]
            d = per[src.upper()]
            d["lines"] += 1
            d["ids"].append(gid)
            d["sizes"].append(len(genes))
            d["genes"].update(genes)
    out = {}
    for src, d in per.items():
        out[src] = {"lines": d["lines"], "unique_ids": len(set(d["ids"])),
                    "duplicate_ids": d["lines"] - len(set(d["ids"])),
                    "median_size": statistics.median(d["sizes"]) if d["sizes"] else 0,
                    "unique_genes": len(d["genes"]),
                    "genes_not_approved_symbol": len(d["genes"] - approved) if approved else None}
    return out


def collect(archive, cache, approved, window):
    cache = Path(cache)
    releases = sorted((d for d in listing(archive) if release_date(d)), key=release_date)
    print(f"{len(releases)} dated releases in the archive")
    for rel in releases:
        base = f"{archive.rstrip('/')}/{rel.strip('/')}/Human/symbol/"
        done_marker = cache / rel.strip("/") / "_done"
        if done_marker.exists():
            continue
        try:
            divisions = [d for d in listing(base) if d.endswith("/")]
        except requests.RequestException as e:
            print(f"skip {rel}: {e}")
            continue
        for div in divisions:
            for f in [x for x in listing(base + div) if x.endswith(".gmt")]:
                out = cache / rel.strip("/") / f"{div.strip('/')}__{f}.json"
                if out.exists():
                    continue
                if not in_window(*window):
                    print("download window closed - stopping; rerun after 9 pm ET to resume")
                    return False
                try:
                    res = summarise(base + div + f, approved)
                except requests.RequestException as e:
                    print(f"  failed {rel}{div}{f}: {e}")
                    continue
                out.parent.mkdir(parents=True, exist_ok=True)
                out.write_text(json.dumps(res))
                time.sleep(1)  # be gentle with the lab server
        done_marker.parent.mkdir(parents=True, exist_ok=True)
        done_marker.write_text("")
        print(f"done {rel}")
    return True


def file_key(name):
    # Human_Reactome_November_01_2026_symbol.gmt -> Human_Reactome_symbol.gmt
    return re.sub(r"_[A-Z][a-z]+_\d{2}_\d{4}", "", name)


def assemble(cache):
    rows = []
    for rel_dir in sorted(Path(cache).iterdir()):
        d = release_date(rel_dir.name)
        if not d:
            continue
        for f in rel_dir.glob("*.json"):
            div, fname = f.stem.split("__", 1)
            for src, v in json.loads(f.read_text()).items():
                rows.append({"release": rel_dir.name, "date": d, "division": div, "file": file_key(fname),
                             "source": src, **v})
    return pd.DataFrame(rows)


def changepoints(series, pen=25):
    """PELT on the series in % of its median, so pen=25 needs roughly a >=5% step to fire;
    slow growth of a percent or so a month is not flagged."""
    import ruptures as rpt
    if len(series) < 6:
        return []
    y = 100 * series.values.astype(float) / (series.median() or 1)
    idx = rpt.Pelt(model="l2", min_size=2).fit(y).predict(pen=pen)[:-1]
    return [series.index[i] for i in idx]


def report(df, outdir):
    out = Path(outdir)
    df.to_csv(out / "release_history.tsv", sep="\t", index=False)

    # one series per source: pathway files + GOALL (avoids double counting GO branch files)
    keep = df[(df["division"] != "GO") | df["file"].str.contains("GOALL")]
    keep = keep.assign(series=lambda x: x["source"] + x["file"].str.extract(r"(no_GO_iea|with_GO_iea)")[0].fillna("").radd(" ").str.rstrip())
    piv = keep.groupby(["date", "series"])["unique_ids"].sum().unstack()
    cps = []
    for s in piv.columns:
        ser = piv[s].dropna()
        for cp in changepoints(ser):
            cps.append({"series": s, "change_point": cp})
    pd.DataFrame(cps).to_csv(out / "change_points.tsv", sep="\t", index=False)

    top = piv.iloc[-1].sort_values(ascending=False).dropna().index[:8]
    fig, axes = plt.subplots(2, 4, figsize=(15, 6.5))
    for ax, s in zip(axes.flat, top):
        ser = piv[s].dropna()
        ax.plot(ser.index, ser.values, color=SERIES[0], marker="o", markersize=3)
        for cp in [c["change_point"] for c in cps if c["series"] == s]:
            ax.axvline(cp, color=SERIES[1], lw=1, ls="--")
        ax.set_title(s, fontsize=10)
        ax.tick_params(axis="x", labelrotation=45, labelsize=7)
    fig.suptitle("Unique gene sets per published release (dashed = detected change point)", color=TEXT2, fontsize=10)
    fig.tight_layout()
    fig.savefig(out / "release_history_sources.png")
    plt.close(fig)

    go = df[df["file"].str.contains("GOALL")].groupby(["date", "file"])[["lines", "unique_ids", "duplicate_ids"]].sum().reset_index()
    if not go.empty:
        fig, ax = plt.subplots(figsize=(9, 4))
        for i, (f, d) in enumerate(go.groupby("file")):
            lab = "no IEA" if "no_GO_iea" in f else "with IEA"
            ax.plot(d["date"], d["unique_ids"], color=SERIES[i], marker="o", markersize=3, label=f"unique GO IDs, {lab}")
            ax.plot(d["date"], d["lines"], color=SERIES[i], ls="--", label=f"lines, {lab}")
        ax.set_title("Published GOALL files: lines vs unique GO IDs")
        ax.legend(fontsize=8)
        fig.savefig(out / "release_history_goall.png")
        plt.close(fig)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--archive", required=True)
    ap.add_argument("--cache", required=True)
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--hgnc", default="raw/reference/hgnc_complete_set.txt")
    ap.add_argument("--window", nargs=2, type=int, default=[21, 8], help="allowed hours ET: start end")
    ap.add_argument("--report-only", action="store_true", help="only rebuild tables/plots from the cache")
    a = ap.parse_args()
    Path(a.outdir).mkdir(parents=True, exist_ok=True)

    if not a.report_only:
        if not in_window(*a.window):
            raise SystemExit(f"It is {datetime.now(ET):%H:%M} ET. The Bader Lab server asks for downloads "
                             f"between {a.window[0]}:00 and {a.window[1]}:00 ET - run this tonight.")
        approved = set()
        if Path(a.hgnc).exists():
            h = pd.read_csv(a.hgnc, sep="\t", dtype=str, low_memory=False).fillna("")
            approved = set(h.loc[h["status"] == "Approved", "symbol"])
        finished = collect(a.archive, a.cache, approved, a.window)
        if not finished:
            raise SystemExit(1)  # incomplete: Snakemake will not mark the output done
    report(assemble(a.cache), a.outdir)


if __name__ == "__main__":
    main()
