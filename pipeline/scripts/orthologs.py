"""Convert a human Entrez GMT to mouse or rat Entrez with Ensembl Compara (replaces frozen HomoloGene).

Rule: keep every one-to-one ortholog; keep one-to-many / many-to-many orthologs only when Ensembl
marks them high-confidence. --method homologene reproduces the legacy conversion for comparison.
Writes <out>.gmt, <out>.mapping.tsv and <out>.manifest.json (Ensembl release, rule, counts).
"""
import argparse
import csv
import gzip
import json
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from common import download, read_gmt, sha256

SPECIES = {
    "mouse": {"taxid": "10090", "ens": "mus_musculus", "file": "Mus_musculus.GRCm39"},
    "rat": {"taxid": "10116", "ens": "rattus_norvegicus", "file": "Rattus_norvegicus.GRCr8"},
}


def fetch(url, dest):
    if not Path(dest).exists():
        download(url, dest)
    return Path(dest)


def ens_entrez(path):
    m = defaultdict(set)
    with gzip.open(path, "rt") as fh:
        for r in csv.DictReader(fh, delimiter="\t"):
            m[r["gene_stable_id"]].add(r["xref"])
    return m


def compara_map(sp, e, cache):
    cfg = SPECIES[sp]
    base = f"https://ftp.ensembl.org/pub/release-{e}/tsv"
    hum = ens_entrez(fetch(f"{base}/homo_sapiens/Homo_sapiens.GRCh38.{e}.entrez.tsv.gz",
                           cache / "ensembl_human_entrez.tsv.gz"))
    oth = ens_entrez(fetch(f"{base}/{cfg['ens']}/{cfg['file']}.{e}.entrez.tsv.gz", cache / f"ensembl_{sp}_entrez.tsv.gz"))
    out = defaultdict(set)
    # release 116 keeps some pairs only in the human dump and others only in the species' dump
    for d, name in [("homo_sapiens", "human"), (cfg["ens"], sp)]:
        dump = fetch(f"{base}/ensembl-compara/homologies/{d}/Compara.{e}.protein_default.homologies.tsv.gz",
                     cache / f"compara_{e}_{name}.tsv.gz")
        with gzip.open(dump, "rt") as fh:
            for r in csv.DictReader(fh, delimiter="\t"):
                t = r["homology_type"]
                if not t.startswith("ortholog"):
                    continue
                if t != "ortholog_one2one" and r["is_high_confidence"] != "1":
                    continue
                pair = {r["species"]: r["gene_stable_id"], r["homology_species"]: r["homology_gene_stable_id"]}
                if "homo_sapiens" in pair and cfg["ens"] in pair:
                    for h in hum.get(pair["homo_sapiens"], ()):
                        out[h] |= oth.get(pair[cfg["ens"]], set())
    return out


def homologene_map(sp, path):
    groups = defaultdict(lambda: defaultdict(set))
    with open(path) as fh:
        for line in fh:
            hid, tax, gid = line.split("\t")[:3]
            groups[hid][tax].add(gid)
    out = defaultdict(set)
    for g in groups.values():
        for h in g.get("9606", ()):
            out[h] |= g.get(SPECIES[sp]["taxid"], set())
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--gmt", required=True, help="human gene sets, Entrez IDs")
    ap.add_argument("--species", required=True, choices=list(SPECIES))
    ap.add_argument("--method", default="compara", choices=["compara", "homologene"])
    ap.add_argument("--ensembl-release", default="116")
    ap.add_argument("--homologene", default="../GeneSetTools/testFiles/homologene.data")
    ap.add_argument("--cache", default="raw/reference")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    m = (compara_map(a.species, a.ensembl_release, Path(a.cache)) if a.method == "compara"
         else homologene_map(a.species, a.homologene))
    sets = list(read_gmt(a.gmt))
    genes = {g for _, _, gs in sets for g in gs}
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    kept = empty = 0
    with open(f"{a.out}.gmt", "w") as fh:
        for name, desc, gs in sets:
            new = sorted({x for g in gs for x in m.get(g, ())})
            if not new:
                empty += 1
                continue
            fh.write(f"{name}\t{desc}\t" + "\t".join(new) + "\n")
            kept += 1
    if kept == 0:
        raise SystemExit("ortholog conversion produced no gene sets")
    with open(f"{a.out}.mapping.tsv", "w") as fh:
        fh.write("human_entrez\t" + a.species + "_entrez\n")
        for g in sorted(genes):
            fh.write(f"{g}\t{'|'.join(sorted(m.get(g, ())))}\n")
    mapped = sum(1 for g in genes if m.get(g))
    manifest = {
        "input": {"file": Path(a.gmt).name, "sha256": sha256(a.gmt)},
        "species": a.species, "method": a.method,
        "ensembl_release": a.ensembl_release if a.method == "compara" else None,
        "rule": "one2one always; one2many/many2many only if is_high_confidence" if a.method == "compara"
                else "HomoloGene groups (frozen)",
        "run_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "human_genes": len(genes), "human_genes_mapped": mapped,
        "pct_mapped": round(100 * mapped / len(genes), 2),
        "gene_sets_written": kept, "gene_sets_dropped_empty": empty,
        "output_sha256": sha256(f"{a.out}.gmt"),
    }
    Path(f"{a.out}.manifest.json").write_text(json.dumps(manifest, indent=2))
    print(json.dumps(manifest, indent=1))


if __name__ == "__main__":
    main()
