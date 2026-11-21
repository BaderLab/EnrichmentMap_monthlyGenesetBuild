"""Parity check between two GMT files (Aim 1.2): per gene set Jaccard index.

Usage: compare_gmt.py OLD.gmt NEW.gmt [--out report.tsv]
Prints a one-line summary; exits 1 if fewer than --min-identical (default 0.99) of sets are identical.
"""
import argparse
import sys

from common import read_gmt


def load(path):
    out = {}
    for name, _desc, genes in read_gmt(path):
        out.setdefault(name, set()).update(genes)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("old")
    ap.add_argument("new")
    ap.add_argument("--out")
    ap.add_argument("--min-identical", type=float, default=0.99)
    a = ap.parse_args()
    old, new = load(a.old), load(a.new)
    names = sorted(set(old) | set(new))
    rows = []
    for n in names:
        o, w = old.get(n, set()), new.get(n, set())
        j = len(o & w) / len(o | w) if (o | w) else 1.0
        rows.append((n, len(o), len(w), round(j, 4)))
    identical = sum(1 for r in rows if r[3] == 1.0 and r[0] in old and r[0] in new)
    only_old = len(set(old) - set(new))
    only_new = len(set(new) - set(old))
    frac = identical / len(names) if names else 1.0
    print(f"{a.old} vs {a.new}: {len(names)} sets, {identical} identical ({frac:.2%}), "
          f"{only_old} only in old, {only_new} only in new")
    if a.out:
        with open(a.out, "w") as fh:
            fh.write("gene_set\tn_old\tn_new\tjaccard\n")
            for r in rows:
                fh.write("\t".join(map(str, r)) + "\n")
    sys.exit(0 if frac >= a.min_identical else 1)


if __name__ == "__main__":
    main()
