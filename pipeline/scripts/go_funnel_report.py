"""Aim 2.2: where does the GO decline come from?

Combines counts.tsv from go_build.py runs over many GO releases (modes: legacy, legacy_keepNOT, fixed)
and writes:
  go_funnel_counts.tsv     all counts, long format
  go_stage_change.tsv      % change first -> last release for every processing stage
  go_genesets_over_time.png  unique GO gene sets: legacy build vs GO itself (reference)
  go_duplicates_over_time.png duplicate GO IDs in GOALL (issue #22)
  go_stage_change.png      % change per stage; if a stage falls faster than GO itself it is the cause
  go_branch_over_time.png  per branch, legacy vs reference
"""
import argparse
from pathlib import Path

import pandas as pd

from plotstyle import SERIES, TEXT2, label_end, plt

STAGE_ORDER = [
    ("obo", "obo_live_terms", "GO terms in ontology (not obsolete)"),
    ("ann", "annotations_in_gaf", "Annotations in GAF"),
    ("ann", "after_NOT_filter", "…after removing NOT"),
    ("ann", "after_evidence_filter", "…after evidence filter (IEA/ND/RCA)"),
    ("sum", "directly_annotated_terms", "Directly annotated terms"),
    ("sum", "sets_in_own_branch", "Gene sets after propagation (own branch)"),
    ("sum", "gene_sets_written", "Gene sets written (incl. other-branch terms)"),
    ("goall", "lines", "GOALL lines (what line counts see)"),
    ("goall", "unique_go_ids", "GOALL unique GO IDs"),
]


def load(files):
    df = pd.concat([pd.read_csv(f, sep="\t", dtype={"release": str}) for f in files], ignore_index=True)
    df["date"] = pd.to_datetime(df["release"])
    return df.sort_values("date")


def stage_value(df, mode, iea, kind, stage):
    d = df[(df["mode"] == mode) & (df["stage"] == stage)]
    if kind == "obo":
        d = df[(df["mode"] == mode) & (df["stage"] == stage)]
        return d.groupby("date")["value"].sum()
    d = d[d["iea"] == iea]
    if kind == "ann":
        d = d[d["branch"] == "bp"]  # same annotation counts in every branch run
        return d.groupby("date")["value"].sum()
    if kind == "goall":
        return d[d["branch"] == "GOALL"].groupby("date")["value"].sum()
    return d[d["branch"].isin(["bp", "mf", "cc"])].groupby("date")["value"].sum()


def pct(s):
    return 100 * (s.iloc[-1] - s.iloc[0]) / s.iloc[0] if len(s) > 1 and s.iloc[0] else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("counts", nargs="+")
    ap.add_argument("--outdir", required=True)
    a = ap.parse_args()
    out = Path(a.outdir)
    out.mkdir(parents=True, exist_ok=True)
    df = load(a.counts)
    df.drop(columns="date").to_csv(out / "go_funnel_counts.tsv", sep="\t", index=False)
    first, last = df["date"].min().date(), df["date"].max().date()

    # ---- stage table ----
    rows = []
    for iea in ["no_GO_iea", "with_GO_iea"]:
        for kind, stage, label in STAGE_ORDER:
            s = stage_value(df, "legacy", iea, kind, stage)
            rows.append({"iea": iea, "stage": label, "first": s.iloc[0], "last": s.iloc[-1],
                         "pct_change": round(pct(s), 2)})
        ref = stage_value(df, "fixed", iea, "goall", "unique_go_ids")
        rows.append({"iea": iea, "stage": "REFERENCE: GO itself (own-branch, non-obsolete)",
                     "first": ref.iloc[0], "last": ref.iloc[-1], "pct_change": round(pct(ref), 2)})
    st = pd.DataFrame(rows)
    st.to_csv(out / "go_stage_change.tsv", sep="\t", index=False)

    # ---- fig 1: unique gene sets over time, legacy vs reference ----
    fig, axes = plt.subplots(1, 2, figsize=(11, 4), sharey=False)
    for ax, iea, title in zip(axes, ["no_GO_iea", "with_GO_iea"], ["No IEA", "With IEA"]):
        series = [
            ("Legacy build: GOALL lines", stage_value(df, "legacy", iea, "goall", "lines"), SERIES[1], "-"),
            ("Legacy build: unique GO IDs", stage_value(df, "legacy", iea, "goall", "unique_go_ids"), SERIES[0], "-"),
            ("GO itself (reference)", stage_value(df, "fixed", iea, "goall", "unique_go_ids"), SERIES[2], "--"),
        ]
        for k, (name, s, c, ls) in enumerate(series):
            ax.plot(s.index, s.values, color=c, ls=ls, marker="o", label=name)
            if k != 1:  # unique IDs sits on top of the reference line; label it once
                label_end(ax, s.index[-1], s.values[-1], f"{s.values[-1]:,}")
        ax.set_title(f"{title}: GO gene sets per release")
        ax.set_ylabel("gene sets")
    axes[0].legend(loc="lower left", fontsize=8)
    fig.suptitle(f"Rebuilt from archived GO releases {first} → {last}", color=TEXT2, fontsize=9, y=0.02)
    fig.savefig(out / "go_genesets_over_time.png")
    plt.close(fig)

    # ---- fig 2: duplicates ----
    fig, ax = plt.subplots(figsize=(7, 3.6))
    for i, (iea, title) in enumerate([("no_GO_iea", "No IEA"), ("with_GO_iea", "With IEA")]):
        s = stage_value(df, "legacy", iea, "goall", "duplicate_go_ids")
        ax.plot(s.index, s.values, color=SERIES[i], marker="o", label=f"Legacy build, {title}")
        label_end(ax, s.index[-1], s.values[-1], f"{s.values[-1]:,}", dy=(-6 if i == 0 else 6))
    if (df["mode"] == "legacy_goBasic").any():
        s = stage_value(df, "legacy_goBasic", "no_GO_iea", "goall", "duplicate_go_ids")
        ax.plot(s.index, s.values, color=SERIES[2], ls="--", marker="o",
                label="Same legacy code with go-basic.obo (proposed fix)")
    ax.set_title("Duplicate GO IDs in GOALL (issue #22)")
    ax.set_ylabel("GO IDs listed twice")
    ax.legend(fontsize=8)
    fig.savefig(out / "go_duplicates_over_time.png")
    plt.close(fig)

    # ---- fig 3: % change per stage ----
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.6), sharey=True)
    for ax, iea, title in zip(axes, ["no_GO_iea", "with_GO_iea"], ["No IEA", "With IEA"]):
        d = st[st["iea"] == iea].iloc[::-1]
        colors = [SERIES[2] if r.startswith("REFERENCE") else SERIES[0] for r in d["stage"]]
        ax.barh(d["stage"], d["pct_change"], color=colors, height=0.6)
        ax.axvline(0, color=TEXT2, lw=0.8)
        for y, v in enumerate(d["pct_change"]):
            ax.annotate(f"{v:+.1f}%", (v, y), xytext=(4 if v >= 0 else -4, 0), textcoords="offset points",
                        ha="left" if v >= 0 else "right", va="center", fontsize=8, color=TEXT2)
        lo, hi = d["pct_change"].min(), d["pct_change"].max()
        ax.set_xlim(min(lo, 0) * 1.45 - 2, max(hi, 0) * 1.2 + 2)
        ax.set_title(f"{title}: change {first} → {last}")
        ax.set_xlabel("% change")
        ax.grid(axis="y", visible=False)
    fig.savefig(out / "go_stage_change.png")
    plt.close(fig)

    # ---- fig 4: per branch ----
    fig, axes = plt.subplots(1, 3, figsize=(13, 3.8))
    for ax, br in zip(axes, ["bp", "mf", "cc"]):
        for i, (mode, stage, name, ls) in enumerate([
                ("legacy", "gene_sets_written", "Legacy: sets written", "-"),
                ("fixed", "gene_sets_written", "GO itself (reference)", "--")]):
            d = df[(df["mode"] == mode) & (df["iea"] == "no_GO_iea") & (df["branch"] == br) & (df["stage"] == stage)]
            ax.plot(d["date"], d["value"], color=SERIES[0 if i == 0 else 2], ls=ls, marker="o", label=name)
        ax.set_title(f"{br.upper()} (no IEA)")
    axes[0].legend(fontsize=8)
    axes[0].set_ylabel("gene sets")
    fig.savefig(out / "go_branch_over_time.png")
    plt.close(fig)
    print(st.to_string(index=False))


if __name__ == "__main__":
    main()
