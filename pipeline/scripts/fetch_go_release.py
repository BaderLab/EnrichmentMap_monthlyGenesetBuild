"""Download one archived GO release (human GAF + go.obo) from release.geneontology.org.

The archive moved the human GAF in 2026 (annotations/goa_human.gaf.gz ->
annotations/gaf/HUMAN-uniprot.gaf.gz), so both paths are tried.
Note: these are the GO Consortium copies of the EBI GOA files the live build uses.
"""
import argparse

from common import download

BASE = "https://release.geneontology.org/{release}/"
GAF_PATHS = ["annotations/goa_human.gaf.gz", "annotations/gaf/HUMAN-uniprot.gaf.gz"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--release", required=True, help="e.g. 2021-01-01 (a folder on release.geneontology.org)")
    ap.add_argument("--gaf", required=True)
    ap.add_argument("--obo", required=True)
    a = ap.parse_args()
    base = BASE.format(release=a.release)

    errors = []
    for p in GAF_PATHS:
        try:
            download(base + p, a.gaf, retries=2, min_bytes=1_000_000)
            break
        except RuntimeError as e:
            errors.append(str(e))
    else:
        raise SystemExit("no human GAF found:\n" + "\n".join(errors))
    download(base + "ontology/go.obo", a.obo, retries=3, min_bytes=1_000_000)


if __name__ == "__main__":
    main()
