"""Offline tests for the adopted mapping (translate.py) and ortholog conversion (orthologs.py)."""
import sys
from pathlib import Path

import pandas as pd
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import orthologs  # noqa: E402
import translate  # noqa: E402

HGNC = pd.DataFrame({
    "symbol": ["PLPP3", "DEFB4A", "DEFB4B", "TP53", "GENEA", "GENEB"],
    "status": ["Approved"] * 6,
    "prev_symbol": ["PPAP2B", "", "", "", "", ""],
    "alias_symbol": ["", "", "", "p53", "SHARED", "SHARED"],
    "entrez_id": ["8613", "1673", "100289462", "7157", "1", "2"],
    "ensembl_gene_id": ["E1", "E2", "E3", "E4", "E5", "E6"],
})
SYMBOL_TO_ACC = {"PLPP3": {"O14495"}, "TP53": {"P04637"}, "DEFB4A": {"O15263"}}
ACC_TO_ENTREZ = {"O14495": {"8613"}, "P04637": {"7157"}, "O15263": {"1673", "100289462"}}


@pytest.fixture
def fake_uniprot(monkeypatch):
    calls = []

    def job(ids, frm, to, taxid=None):
        calls.append((frm, to, list(ids)))
        table = SYMBOL_TO_ACC if frm == "Gene_Name" else ACC_TO_ENTREZ
        return {i: set(table[i]) for i in ids if i in table}
    monkeypatch.setattr(translate, "_job", job)
    translate.RECOVERED.clear()
    return calls


def test_renamed_symbol_is_rescued(fake_uniprot):
    m, st, notes = translate.map_ids(["PPAP2B"], "symbol", ["entrezgene"], "9606", HGNC)
    assert m["entrezgene"]["PPAP2B"] == {"8613"}
    assert st["PPAP2B"] == "rescued_previous"
    # the query sent to UniProt was the current symbol
    assert fake_uniprot[0][2] == ["PLPP3"]


def test_shared_alias_is_not_guessed(fake_uniprot):
    m, st, _ = translate.map_ids(["SHARED"], "symbol", ["entrezgene"], "9606", HGNC)
    assert "SHARED" not in m["entrezgene"]
    assert st["SHARED"] == "ambiguous"


def test_symbol_mapping_to_two_genes_uses_hgnc_id(fake_uniprot):
    m, _, _ = translate.map_ids(["DEFB4A"], "symbol", ["entrezgene"], "9606", HGNC)
    assert m["entrezgene"]["DEFB4A"] == {"1673"}


def test_protein_of_duplicated_genes_keeps_both(fake_uniprot):
    m, st, _ = translate.map_ids(["O15263"], "uniprot", ["entrezgene"], "9606")
    assert m["entrezgene"]["O15263"] == {"1673", "100289462"}
    assert st["O15263"] == "multi_kept"


def test_incomplete_job_is_asked_again(monkeypatch):
    translate.RECOVERED.clear()
    seen = []

    def flaky(ids, frm, to, taxid=None):
        seen.append(list(ids))
        ids = ids[: len(ids) // 2] if len(seen) == 1 else ids  # first answer drops half
        return {i: {"G" + i} for i in ids}
    monkeypatch.setattr(translate, "_job", flaky)
    out = translate.uniprot_map([f"A{i}" for i in range(10)], "UniProtKB_AC-ID", "GeneID", "9606")
    assert len(out) == 10
    assert translate.RECOVERED["UniProtKB_AC-ID->GeneID"] == 5


def test_failed_job_raises_after_retries(monkeypatch):
    def broken(*a, **k):
        raise RuntimeError("UniProt job failed")
    monkeypatch.setattr(translate, "_job", broken)
    monkeypatch.setattr(translate.time, "sleep", lambda s: None)
    with pytest.raises(RuntimeError):
        translate.uniprot_map(["A1"], "UniProtKB_AC-ID", "GeneID", "9606")


def test_homologene_groups(tmp_path):
    hg = tmp_path / "homologene.data"
    hg.write_text("1\t9606\t7157\tTP53\n1\t10090\t22059\tTrp53\n2\t9606\t8613\tPLPP3\n")
    m = orthologs.homologene_map("mouse", hg)
    assert m["7157"] == {"22059"}
    assert not m.get("8613")
