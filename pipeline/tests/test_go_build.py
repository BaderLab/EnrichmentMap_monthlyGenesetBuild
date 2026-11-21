"""Toy ontology that reproduces issue #22: an MF term part_of a BP term makes the legacy MF file
contain the BP term too, so GOALL lists it twice. Run: python -m pytest pipeline/tests"""
import gzip
import subprocess
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))

import go_build  # noqa: E402

OBO = """format-version: 1.2

[Term]
id: GO:0000001
name: bp root
namespace: biological_process

[Term]
id: GO:0000002
name: bp child
namespace: biological_process
is_a: GO:0000001 ! bp root

[Term]
id: GO:0000010
name: mf term
namespace: molecular_function
relationship: part_of GO:0000002 ! bp child

[Term]
id: GO:0000099
name: old term
namespace: biological_process
is_obsolete: true

[Typedef]
id: part_of
name: part of
"""

GAF_ROWS = [
    # db, id, symbol, qualifier, goid, ref, evidence, with, aspect, ... (15 columns)
    ("UniProtKB", "P1", "GENE1", "", "GO:0000002", "PMID:1", "IDA", "", "P"),
    ("UniProtKB", "P2", "GENE2", "", "GO:0000010", "PMID:1", "IDA", "", "F"),
    ("UniProtKB", "P3", "GENE3", "NOT", "GO:0000002", "PMID:1", "IDA", "", "P"),
    ("UniProtKB", "P4", "GENE4", "", "GO:0000002", "PMID:1", "IEA", "", "P"),
]


def write_inputs(tmp):
    (tmp / "go.obo").write_text(OBO)
    with gzip.open(tmp / "g.gaf.gz", "wt") as fh:
        fh.write("!gaf-version: 2.2\n")
        for r in GAF_ROWS:
            fh.write("\t".join(list(r) + ["", "", "protein", "taxon:9606", "20260101", "GO"]) + "\n")


def counts(path):
    out = {}
    for line in Path(path).read_text().splitlines()[1:]:
        _rel, mode, iea, branch, stage, v = line.split("\t")
        out[(iea, branch, stage)] = int(v)
    return out


def run(tmp, *flags):
    out = tmp / "_".join(flags or ("default",))
    subprocess.run([sys.executable, str(SCRIPTS / "go_build.py"), "--gaf", str(tmp / "g.gaf.gz"),
                    "--obo", str(tmp / "go.obo"), "--outdir", str(out), *flags], check=True)
    return out


def test_legacy_reproduces_cross_branch_duplicates(tmp_path):
    write_inputs(tmp_path)
    c = counts(run(tmp_path, "--mode", "legacy") / "counts.tsv")
    # MF file holds GO:0000010 plus BP terms 0000002 and 0000001 reached through part_of
    assert c[("with_GO_iea", "mf", "sets_from_other_branch")] == 2
    assert c[("with_GO_iea", "GOALL", "duplicate_go_ids")] == 2


def test_fixed_mode_has_no_duplicates(tmp_path):
    write_inputs(tmp_path)
    c = counts(run(tmp_path, "--mode", "fixed") / "counts.tsv")
    assert c[("with_GO_iea", "GOALL", "duplicate_go_ids")] == 0
    assert c[("with_GO_iea", "GOALL", "unique_go_ids")] == 3


def test_not_and_iea_filters(tmp_path):
    write_inputs(tmp_path)
    out = run(tmp_path, "--mode", "fixed")
    bp_with = (out / "Human_GO_bp_with_GO_iea_UniProt.gmt").read_text()
    bp_no = (out / "Human_GO_bp_no_GO_iea_UniProt.gmt").read_text()
    assert "P3" not in bp_with            # NOT annotation dropped
    assert "P4" in bp_with and "P4" not in bp_no   # IEA only in with_GO_iea
    assert "P1" in bp_no.split("GO:0000001")[1].split("\n")[0]  # propagated to the root


def test_obo_parser_skips_nothing_needed():
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "x.obo"
        p.write_text(OBO)
        t = go_build.parse_obo(p)
    assert t["GO:0000010"]["parents"] == {"GO:0000002"}
    assert t["GO:0000099"]["obsolete"]
