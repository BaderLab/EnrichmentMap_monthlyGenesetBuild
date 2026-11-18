"""Build GO gene sets from a GAF + OBO file and count what survives each step.

Two modes:
  legacy  - a line-for-line port of GOGeneSetFileMaker.java + Ontology.java (what the
            monthly build does today). Parents = is_a + part_of across ALL namespaces,
            so e.g. the MF file also gets BP terms that an MF term is part_of.
  fixed   - the same, but a branch file only contains non-obsolete terms from its own
            namespace, and annotations only propagate along edges inside that namespace.
            This is the "what GO itself contains" reference for the attrition analysis.

Outputs (in --outdir):
  Human_GO_{bp,mf,cc}_{with,no}_GO_iea_UniProt.gmt   (and _symbol.gmt from GAF column 3)
  Human_GOALL_{with,no}_GO_iea_UniProt.gmt           (cat of the three branches, as release.sh does)
  counts.tsv                                          one row per (iea, branch, stage)
"""
import argparse
import csv
from collections import defaultdict
from pathlib import Path

from common import open_text

BRANCH_ASPECT = {"bp": "P", "mf": "F", "cc": "C"}
BRANCH_NS = {"bp": "biological_process", "mf": "molecular_function", "cc": "cellular_component"}
EXCLUDED_EVIDENCE = {"IEA", "RCA", "ND"}
IEA_LABEL = {True: "no_GO_iea", False: "with_GO_iea"}  # key = exclude?


def parse_obo(path):
    """Return {id: dict(name, namespace, obsolete, parents)} for every stanza with an id
    (Term and Typedef, like Ontology.java). parents = is_a + 'relationship: part_of'."""
    terms = {}
    cur = None
    with open_text(path) as fh:
        for raw in fh:
            line = raw.rstrip("\n")
            if line.startswith("[") or line == "":
                cur = None
                if line.startswith("["):
                    cur = {"stanza": line, "id": None, "name": "", "namespace": "",
                           "obsolete": False, "parents": set()}
                continue
            if cur is None:
                continue
            if line.startswith("id:"):
                cur["id"] = line.split("id:", 1)[1].strip()
                terms[cur["id"]] = cur
            elif line.startswith("name:"):
                cur["name"] = line.split("name:", 1)[1].strip()
            elif line.startswith("namespace:"):
                cur["namespace"] = line.split("namespace:", 1)[1].strip()
            elif line.startswith("is_obsolete:") and "true" in line:
                cur["obsolete"] = True
            elif line.startswith("is_a:"):
                cur["parents"].add(line.split("is_a:", 1)[1].split("!")[0].strip())
            elif line.startswith("relationship: part_of"):
                cur["parents"].add(line.split("relationship: part_of", 1)[1].split("!")[0].strip())
    return terms


def ancestors_fn(parents):
    """Memoised transitive closure: term -> set of ancestors (not including itself)."""
    memo = {}

    def anc(t):
        if t in memo:
            return memo[t]
        memo[t] = set()  # guards against cycles
        out = set()
        for p in parents.get(t, ()):
            out.add(p)
            out |= anc(p)
        memo[t] = out
        return out
    return anc


def read_gaf(path):
    """Yield (uniprot, symbol, qualifier, goid, evidence, aspect) for lines with >=15 columns."""
    with open_text(path) as fh:
        for line in fh:
            if line.startswith("!"):
                continue
            t = line.rstrip("\n").split("\t")
            if len(t) < 15:
                continue
            yield t[1], t[2], t[3], t[4], t[6], t[8]


def build(gaf_rows, terms, branch, exclude, mode, keep_not=False):
    """Return (sets, stats). sets = {goid: (uniprot_set, symbol_set)}."""
    stats = defaultdict(int)
    direct_u = defaultdict(set)
    direct_s = defaultdict(set)
    for uni, sym, qual, goid, ev, aspect in gaf_rows:
        stats["annotations_in_gaf"] += 1
        if "NOT" in qual and not keep_not:
            continue
        stats["after_NOT_filter"] += 1
        if exclude and ev.upper() in EXCLUDED_EVIDENCE:
            continue
        stats["after_evidence_filter"] += 1
        if aspect.upper() != BRANCH_ASPECT[branch]:
            continue
        stats["after_branch_filter"] += 1
        if goid.lower() == "all":
            continue
        direct_u[goid].add(uni)
        direct_s[goid].add(sym)
    stats["directly_annotated_terms"] = len(direct_u)

    ns = BRANCH_NS[branch]
    if mode == "legacy":
        parents = {t: v["parents"] for t, v in terms.items()}
        candidates = set(terms) | set(direct_u)
    else:
        ok = {t for t, v in terms.items() if v["namespace"] == ns and not v["obsolete"]}
        parents = {t: {p for p in terms[t]["parents"] if p in ok} for t in ok}
        candidates = ok
        direct_u = {g: s for g, s in direct_u.items() if g in ok}
        direct_s = {g: s for g, s in direct_s.items() if g in ok}

    anc = ancestors_fn(parents)
    prop_u = defaultdict(set)
    prop_s = defaultdict(set)
    for goid in direct_u:
        for t in {goid} | anc(goid):
            prop_u[t] |= direct_u[goid]
            prop_s[t] |= direct_s[goid]

    sets = {t: (prop_u[t], prop_s[t]) for t in candidates if prop_u.get(t)}
    stats["gene_sets_written"] = len(sets)
    for t in sets:
        info = terms.get(t)
        if info is None:
            stats["sets_not_in_obo"] += 1
        elif info["obsolete"]:
            stats["sets_obsolete_term"] += 1
        elif info["namespace"] != ns:
            stats["sets_from_other_branch"] += 1
        else:
            stats["sets_in_own_branch"] += 1
    stats["unique_genes"] = len(set().union(*(u for u, _ in sets.values()))) if sets else 0
    return sets, stats


def write_gmt(path, sets, terms, branch, which):
    with open(path, "w") as fh:
        for goid in sorted(sets):
            name = terms[goid]["name"] if goid in terms else "GO ID not found in OBO file ontology definitions"
            genes = sorted(sets[goid][which])
            fh.write(f"{name}%GO{branch}%{goid}\t{name}\t" + "\t".join(genes) + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--gaf", required=True)
    ap.add_argument("--obo", required=True)
    ap.add_argument("--mode", choices=["legacy", "fixed"], default="legacy")
    ap.add_argument("--release", default="", help="label written into counts.tsv")
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--no-gmt", action="store_true", help="only write counts.tsv")
    ap.add_argument("--keep-not", action="store_true",
                    help="keep NOT-qualified annotations, as the build did before commit 59afacb (Sept 2024)")
    a = ap.parse_args()

    out = Path(a.outdir)
    out.mkdir(parents=True, exist_ok=True)
    mode_label = a.mode + ("_keepNOT" if a.keep_not else "") + ("_goBasic" if "go-basic" in a.obo else "")
    terms = parse_obo(a.obo)
    gaf_rows = list(read_gaf(a.gaf))

    rows = []
    for ns_key, ns in BRANCH_NS.items():
        n = sum(1 for v in terms.values() if v["namespace"] == ns and not v["obsolete"])
        rows.append(("", ns_key, "obo_live_terms", n))
    rows.append(("", "all", "obo_obsolete_terms", sum(1 for v in terms.values() if v["obsolete"])))

    for exclude in (False, True):
        label = IEA_LABEL[exclude]
        goall_ids = []
        goall_lines = []
        for branch in BRANCH_NS:
            sets, stats = build(gaf_rows, terms, branch, exclude, a.mode, a.keep_not)
            for k, v in stats.items():
                rows.append((label, branch, k, v))
            goall_ids += list(sets)
            if not a.no_gmt:
                stem = out / f"Human_GO_{branch}_{label}_UniProt"
                write_gmt(f"{stem}.gmt", sets, terms, branch, 0)
                write_gmt(f"{stem}_symbol.gmt", sets, terms, branch, 1)
                goall_lines.append(Path(f"{stem}.gmt").read_text())
        rows.append((label, "GOALL", "lines", len(goall_ids)))
        rows.append((label, "GOALL", "unique_go_ids", len(set(goall_ids))))
        rows.append((label, "GOALL", "duplicate_go_ids", len(goall_ids) - len(set(goall_ids))))
        if not a.no_gmt:
            (out / f"Human_GOALL_{label}_UniProt.gmt").write_text("".join(goall_lines))

    written = sum(v for _, _, stage, v in rows if stage == "gene_sets_written")
    if written == 0:
        raise SystemExit(f"go_build: 0 gene sets written from {a.gaf} - refusing to produce empty output")

    with open(out / "counts.tsv", "w", newline="") as fh:
        w = csv.writer(fh, delimiter="\t")
        w.writerow(["release", "mode", "iea", "branch", "stage", "value"])
        for iea, branch, stage, v in rows:
            w.writerow([a.release, mode_label, iea, branch, stage, v])


if __name__ == "__main__":
    main()
