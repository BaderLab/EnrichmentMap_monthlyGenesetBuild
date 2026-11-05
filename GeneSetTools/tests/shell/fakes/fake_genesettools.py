#!/usr/bin/env python3
"""Stand-in for 'java -jar GenesetTools.jar <command> ...' used by the shell tests.

It doesn't parse BioPAX or call UniProt. It writes output files with the same
NAMES the real GenesetTools uses (the build script finds files by name), and
small but real contents, so the build script's file handling can be tested end
to end. Every call is logged to $FAKE_JAVA_LOG as: command<TAB>args.

Failure injection (for testing the script's error handling):
  FAKE_FAIL_TOGSEA=<substring>     toGSEA exits 1, writes nothing, if --biopax contains it
  FAKE_FAIL_TRANSLATE=<substring>  translate exits 1, writes nothing, if --gmt contains it
  FAKE_FAIL_CONVERT=<substring>    convertGeneSets exits 1, writes nothing, if --gmt contains it
"""
import os
import sys

argv = sys.argv[1:]
if "-jar" not in argv:
    sys.stderr.write("fake java: only -jar GenesetTools.jar is supported\n")
    sys.exit(2)
k = argv.index("-jar")
command = argv[k + 2] if len(argv) > k + 2 else ""
rest = argv[k + 3:]

log = os.environ.get("FAKE_JAVA_LOG")
if log:
    with open(log, "a") as fh:
        fh.write(command + "\t" + " ".join(rest) + "\t" + os.getcwd() + "\n")


def opt(name, default=None):
    if name in rest:
        return rest[rest.index(name) + 1]
    return default


def failing(env, value):
    pat = os.environ.get(env)
    return bool(pat) and value is not None and pat in value


def read_gmt(path):
    with open(path) as fh:
        return [l.rstrip("\n").split("\t") for l in fh if l.strip()]


if command == "toGSEA":
    biopax = opt("--biopax")
    out = opt("--outfile")
    if failing("FAKE_FAIL_TOGSEA", biopax) or not os.path.isfile(biopax):
        sys.stderr.write("fake toGSEA: could not parse %s\n" % biopax)
        sys.exit(1)
    source = opt("--source")
    if source == "Panther":
        source = "PANTHER Pathway"
    stem = os.path.basename(biopax).split(".")[0]
    with open(out, "w") as fh:
        # the '/' is there so the script's slash removal can be checked
        fh.write("%s pathway%%%s%%%s\t%s pathway\tP04637\tQ9Y6K9/X\n" % (stem, source, stem, stem))
    sys.exit(0)

if command == "translate":
    gmt = opt("--gmt")
    old_id = opt("--oldID")
    new_id = opt("--newID")
    if failing("FAKE_FAIL_TRANSLATE", gmt):
        sys.stderr.write("Something is wrong with the uniprot web service: fake failure\n")
        sys.exit(1)
    if not os.path.isfile(gmt):
        sys.stderr.write("unable to load GMT file %s\n" % gmt)
        sys.exit(1)
    old_print = {"uniprot": "uniprot", "symbol": "symbol", "entrezgene": "entrezgene",
                 "ensembl": "ensembl"}[old_id.lower()]
    new_print = {"uniprot": "UniProt", "symbol": "symbol", "entrezgene": "entrezgene",
                 "ensembl": "ensembl"}[new_id.lower()]
    # same naming as GeneSetTranslator_UniprotREST.outputFiles()
    base = gmt.split(".gmt")[0]
    idx = base.lower().find(old_print.lower())
    newbase = "".join(c for r, c in enumerate(base) if r < idx or r > idx + len(old_print))
    rows = read_gmt(gmt)
    with open(newbase + new_print + ".gmt", "w") as fh:
        for r in rows:
            fh.write("\t".join(r[:2] + [g + "_" + new_print for g in r[2:]]) + "\n")
    genes = set(g for r in rows for g in r[2:])
    with open(newbase + new_print + "_summary.log", "w") as fh:
        fh.write("File name:\t%s\noriginal Identifier\t%s\nID translated to\t%s\n"
                 "Total number of genes in the file:\t%d\n"
                 "total Number of genes in file successfully converted:\t%d\n"
                 "total Number Identifiers unable to map\t0\nPercentage ids not translated\t0.0%%\n"
                 "Total annotations in the file\t%d\nTotal Untranslated annotations\t0\n"
                 "Percentage annotations not translated\t0.0%%\n===================================\n"
                 % (gmt, old_id, new_print, len(genes), len(genes), sum(len(r) - 2 for r in rows)))
    with open(newbase + new_print + "_detailed.log", "w") as fh:
        fh.write("GeneSetName \t Number of genes queried \t Number of unfound source ids \t list of unfound source ids \n")
    sys.exit(0)

if command == "convertGeneSets":
    gmt = opt("--gmt")
    out = opt("--outfile")
    taxid = opt("--newtaxid")
    if failing("FAKE_FAIL_CONVERT", gmt) or not os.path.isfile(gmt):
        sys.stderr.write("fake convertGeneSets failed for %s\n" % gmt)
        sys.exit(1)
    rows = read_gmt(gmt)
    with open(out, "w") as fh:
        for r in rows:
            fh.write("\t".join(r) + "\n")
    with open(out + taxid + "_conversion.log", "w") as fh:
        fh.write("File name:\t%s\nconverted to taxid\t%s\nnumber of genesets\t%d\n" % (gmt, taxid, len(rows)))
    sys.exit(0)

if command == "createGo":
    taxid = opt("--organism")
    branch = opt("--branch")
    prefix = {"9606": "Human_GO", "10090": "MOUSE_GO", "10116": "RAT_GO"}.get(taxid, taxid + "_GO")
    iea = "no_GO_iea" if "--exclude" in rest else "with_GO_iea"
    name = "%s_%s_%s_UniProt" % (prefix, branch, iea)
    for fn in (name + ".gmt", name + "_symbol.gmt"):
        with open(fn, "w") as fh:
            fh.write("GO %s term%%GO%s%%GO:0000001\tGO %s term\tP04637\tP38398\n" % (branch, branch, branch))
    sys.exit(0)

if command == "createHPO":
    with open(opt("--outfile"), "w") as fh:
        fh.write("Phenotype%HPO%HP:0000001\tPhenotype\t7157\t672\n")
    sys.exit(0)

sys.stderr.write("fake java: unknown command %s\n" % command)
sys.exit(1)
