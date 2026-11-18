# Modular gene set build (Aim 1) and change tracking (Aim 2)

A Snakemake workflow that sits next to the legacy `GeneSetTools/create_genesets_release.sh`.
It doesn't replace the legacy build yet. Each source is its own job, so one failure no longer
stops the rest, and the Aim 2 analyses run from the same code.

## Setup

```bash
cd pipeline
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m pytest tests          # GO module tests (toy ontology reproducing issue #22)
```

## What to run

| Command | What it does | When |
|---|---|---|
| `snakemake -c4 --keep-going monthly_raw` | Aim 1.2: downloads every live source in `sources.yaml` (one job per file, retries, SHA-256 + provenance JSON) and builds GO with the Python GO module | any time |
| `snakemake -c4 go_funnel` | Aim 2.2: rebuilds GO from 17 archived GO releases (2021–2026) and counts gene sets after every step | any time |
| `snakemake -c4 mapping_benchmark` | Aim 2.1: legacy UniProt vs BridgeDb vs Ensembl vs g:Profiler, plus a labelled 200-symbol test set | any time |
| `snakemake -c1 orthology_benchmark` | Aim 2.1: HomoloGene (legacy) vs Ensembl Compara for mouse/rat, plus woodchuck table coverage | any time |
| `snakemake -c1 source_status` | Aim 2.3: classifies every source (active/static/unavailable) and plots identifier decay of static sources | monthly, after `monthly_raw` |
| `snakemake -c1 release_history` | Aim 2.2: summarises every published Bader Lab release | **only 9 pm–8 am ET** (the server's rule). It refuses to start outside that window, stops when the window closes, and resumes the next night. |

## Files

- `sources.yaml`: source registry (Aim 1.1). Adding or retiring a source means editing one entry.
- `scripts/go_build.py`: Python port of `GOGeneSetFileMaker.java` + `Ontology.java`. Checked against the
  Java tool on GO release 2026-08-05: **21,466 / 21,466 no-IEA gene sets identical** for UniProt and symbol output.
  `--mode fixed` keeps each branch to its own non-obsolete terms (the reference for "what GO contains").
- `scripts/compare_gmt.py`: per-gene-set Jaccard parity check between two GMT files.
- `results/aim2/…`: tables (`.tsv`) and figures (`.png`) for each analysis.

## Findings so far (18 Nov 2026)

**GO decline (2.2): it comes from GO, not from the pipeline.** Across the 17 rebuilt GO releases, the legacy
build's unique GO IDs stay within 0.31% of GO's own count (`go_genesets_over_time.png`). From Jan 2021 to Aug 2026:
legacy −1.2% vs GO −1.1% (no IEA); legacy −5.6% vs GO −5.6% (with IEA). The larger drop that line
counts show (−2.9% / −7.2%) is an artifact of the duplicates. The NOT-filter fix (Sept 2024) removes only
~40 gene sets.

**Duplicates (issue #22): cause found, one-line fix.** The legacy code follows `part_of` links across GO
branches, so the MF file also contains BP terms. That gives ~1,300 duplicate IDs in every GOALL file
(1,282 / 1,330 in Aug 2026). The code has done this since 2012. The duplicates appeared in Oct 2021
because commit `f13239b` (May 2021) switched from `gene_ontology.1_2.obo` to the full `go.obo`, which has
those cross-branch links. Running the **same legacy code with `go-basic.obo`** gives **0 duplicates** in every
release, and its counts match the GO reference to within 15 gene sets (`go_duplicates_over_time.png`).

**Mapping (2.1).** UniProt → Entrez on 19,776 GO accessions: the legacy UniProt service is the most
accurate (95.4% agree with HGNC, 0.5% one-to-many). Ensembl 94.2%, BridgeDb 93.9%, g:Profiler 91.8%.
On symbols, only the legacy route resolves renamed genes (76%). BridgeDb and g:Profiler resolve 0% of
previous symbols. But the legacy route silently picks one gene for 22% of shared aliases.

**Decay (2.3).** 5.2% of the July 2011 KEGG symbols are no longer approved HGNC symbols; checking previous
symbols would rescue all but 0.8%. NCI-Nature: 1.9% of UniProt IDs; IOB: 0.1% of Entrez IDs.

**Failure isolation (1.2).** With Panther's URL broken on purpose, the other 11 downloads and the GO build
finished. Rerunning after the fix ran only the Panther job.

## Not done yet

- BioPAX (Reactome, Panther, PathBank, HumanCyc, NCI, NetPath, IOB), HPO and DrugBank are still parsed by the
  legacy Java/Perl tools. `sources.yaml` marks them `*_legacy`.
- Translation of the GO module's UniProt output to Entrez/symbol/Ensembl, and mouse/rat/woodchuck conversion.
- Provenance manifests (Aim 1.3). The `.download.json` files already hold the URL, time, `Last-Modified` and SHA-256.
- `release_history` hasn't run yet because of the download window.
- Caveats: the archived GO files are the GO Consortium copies of the EBI GOA files the live build downloads.
  BioMart was unreachable on 18 Nov 2026, so the benchmark uses Ensembl 116 release files. BridgeDb's
  data is Ensembl 111 (built Oct 2024).
