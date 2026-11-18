"""Aim 2.1 (step 4): cross-species translation benchmark.

Mouse / rat: the build converts human gene sets with HomoloGene (frozen; NCBI retired it in 2014).
Compared here with Ensembl Compara orthologues (current release), both expressed in Entrez Gene IDs.
Universe = HGNC approved protein-coding genes with an Entrez ID.

Per species and method:
  coverage_pct        human genes with >=1 ortholog
  one_to_one_pct      of those, exactly one ortholog
  dead_target_pct     ortholog Entrez IDs no longer in NCBI gene_info for that species (decay)
  agree_pct           genes mapped by both methods whose ortholog sets overlap
Woodchuck: coverage of the universe and how many human IDs in the 2021 table are still current.
"""
import argparse
import csv
import gzip
from collections import defaultdict
from pathlib import Path

import pandas as pd

from common import download
from plotstyle import SERIES, TEXT2, plt

SPECIES = {
    "mouse": {"taxid": "10090", "ens": "mus_musculus", "ens_file": "Mus_musculus.GRCm39",
              "ncbi": "Mus_musculus"},
    "rat": {"taxid": "10116", "ens": "rattus_norvegicus", "ens_file": "Rattus_norvegicus.GRCr8",
            "ncbi": "Rattus_norvegicus"},
}


def fetch(url, dest):
    if not Path(dest).exists():
        download(url, dest)
    return dest


def ens_entrez(path):
    m = defaultdict(set)
    with gzip.open(path, "rt") as fh:
        for r in csv.DictReader(fh, delimiter="\t"):
            m[r["gene_stable_id"]].add(r["xref"])
    return m


def live_ncbi_ids(path):
    ids = set()
    with gzip.open(path, "rt") as fh:
        for line in fh:
            if not line.startswith("#"):
                ids.add(line.split("\t", 2)[1])
    return ids


def homologene(path, taxid):
    groups = defaultdict(lambda: defaultdict(set))
    with open(path) as fh:
        for line in fh:
            hid, tax, gid = line.split("\t")[:3]
            groups[hid][tax].add(gid)
    out = defaultdict(set)
    for g in groups.values():
        for h in g.get("9606", ()):
            out[h] |= g.get(taxid, set())
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ensembl-release", required=True)
    ap.add_argument("--homologene", required=True)
    ap.add_argument("--woodchuck", required=True)
    ap.add_argument("--hgnc", required=True)
    ap.add_argument("--cache", required=True)
    ap.add_argument("--outdir", required=True)
    a = ap.parse_args()
    e = a.ensembl_release
    cache, out = Path(a.cache), Path(a.outdir)
    out.mkdir(parents=True, exist_ok=True)

    h = pd.read_csv(a.hgnc, sep="\t", dtype=str, low_memory=False).fillna("")
    h = h[(h["status"] == "Approved")]
    current_human = set(h.loc[h["entrez_id"] != "", "entrez_id"])
    universe = set(h.loc[(h["locus_group"] == "protein-coding gene") & (h["entrez_id"] != ""), "entrez_id"])

    hum = ens_entrez(fetch(f"https://ftp.ensembl.org/pub/release-{e}/tsv/homo_sapiens/Homo_sapiens.GRCh38.{e}.entrez.tsv.gz",
                           cache / "ensembl_human_entrez.tsv.gz"))

    rows = []
    for sp, cfg in SPECIES.items():
        other = ens_entrez(fetch(f"https://ftp.ensembl.org/pub/release-{e}/tsv/{cfg['ens']}/{cfg['ens_file']}.{e}.entrez.tsv.gz",
                                 cache / f"ensembl_{sp}_entrez.tsv.gz"))
        live = live_ncbi_ids(fetch(f"https://ftp.ncbi.nlm.nih.gov/gene/DATA/GENE_INFO/Mammalia/{cfg['ncbi']}.gene_info.gz",
                                   cache / f"ncbi_{sp}_gene_info.gz"))
        # the species' own dump (mouse is missing from the human file in release 116)
        compara = fetch(f"https://ftp.ensembl.org/pub/release-{e}/tsv/ensembl-compara/homologies/{cfg['ens']}/"
                        f"Compara.{e}.protein_default.homologies.tsv.gz", cache / f"compara_{e}_{sp}.tsv.gz")
        comp = defaultdict(set)
        with gzip.open(compara, "rt") as fh:
            for r in csv.DictReader(fh, delimiter="\t"):
                if not r["homology_type"].startswith("ortholog"):
                    continue
                pair = {r["species"]: r["gene_stable_id"], r["homology_species"]: r["homology_gene_stable_id"]}
                if "homo_sapiens" not in pair or cfg["ens"] not in pair:
                    continue
                for hg in hum.get(pair["homo_sapiens"], ()):
                    comp[hg] |= other.get(pair[cfg["ens"]], set())
        hg = homologene(a.homologene, cfg["taxid"])
        methods = {"HomoloGene (legacy)": hg, "Ensembl Compara": comp}
        for name, m in methods.items():
            mapped = [g for g in universe if m.get(g)]
            targets = set().union(*(m[g] for g in mapped)) if mapped else set()
            rows.append({"species": sp, "method": name, "universe": len(universe),
                         "coverage_pct": round(100 * len(mapped) / len(universe), 2),
                         "one_to_one_pct": round(100 * sum(len(m[g]) == 1 for g in mapped) / max(len(mapped), 1), 2),
                         "dead_target_pct": round(100 * len(targets - live) / max(len(targets), 1), 2)})
        both = [g for g in universe if hg.get(g) and comp.get(g)]
        agree = sum(bool(hg[g] & comp[g]) for g in both)
        rows.append({"species": sp, "method": "agreement (both map)", "universe": len(both),
                     "coverage_pct": None, "one_to_one_pct": None, "dead_target_pct": None,
                     "agree_pct": round(100 * agree / max(len(both), 1), 2),
                     "only_homologene": sum(1 for g in universe if hg.get(g) and not comp.get(g)),
                     "only_compara": sum(1 for g in universe if comp.get(g) and not hg.get(g))})

    wc = pd.read_csv(a.woodchuck, sep="\t", header=None, dtype=str, comment=None)
    wc = wc[wc[0] != "homoloGroup"]
    hum_ids = set(wc.loc[wc[1] == "9606", 2])
    grp_with_wc = set(wc.loc[wc[1] == "9995", 0])
    hum_mapped = set(wc.loc[(wc[1] == "9606") & (wc[0].isin(grp_with_wc)), 2])
    rows.append({"species": "woodchuck", "method": "2021 homology table (legacy)", "universe": len(universe),
                 "coverage_pct": round(100 * len(hum_mapped & universe) / len(universe), 2),
                 "stale_human_ids_pct": round(100 * len(hum_ids - current_human) / max(len(hum_ids), 1), 2)})

    df = pd.DataFrame(rows)
    df.to_csv(out / "orthology_summary.tsv", sep="\t", index=False)
    print(df.to_string(index=False))

    d = df[df["method"].isin(["HomoloGene (legacy)", "Ensembl Compara"])]
    fig, axes = plt.subplots(1, 3, figsize=(12, 3.3))
    for ax, col, title in zip(axes, ["coverage_pct", "one_to_one_pct", "dead_target_pct"],
                              ["Human genes with an ortholog", "…exactly one ortholog", "Ortholog IDs retired at NCBI"]):
        for i, m in enumerate(["HomoloGene (legacy)", "Ensembl Compara"]):
            vals = [d[(d["species"] == s) & (d["method"] == m)][col].squeeze() for s in SPECIES]
            xs = [k + (i - 0.5) * 0.36 for k in range(len(SPECIES))]
            ax.bar(xs, vals, width=0.33, color=SERIES[i], label=m)
            for x, v in zip(xs, vals):
                ax.annotate(f"{v:.1f}", (x, v), xytext=(0, 3), textcoords="offset points", ha="center",
                            fontsize=8, color=TEXT2)
        ax.set_xticks(range(len(SPECIES)), [s.capitalize() for s in SPECIES])
        ax.set_title(title)
        ax.set_ylabel("%")
        ax.grid(axis="x", visible=False)
    axes[0].legend(fontsize=8, loc="lower left")
    fig.suptitle(f"{len(universe):,} human protein-coding genes; Ensembl release {e}", color=TEXT2, fontsize=9, y=0.02)
    fig.savefig(out / "orthology.png")
    plt.close(fig)


if __name__ == "__main__":
    main()
