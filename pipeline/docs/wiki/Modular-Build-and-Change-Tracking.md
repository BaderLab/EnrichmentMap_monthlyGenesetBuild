# Modular Build and Change Tracking

*Status as of 18 November 2026. Code lives in [`pipeline/`](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/tree/feature/reena-bugfixes/pipeline) on the `feature/reena-bugfixes` branch.*

This page covers the new Snakemake workflow that runs next to the legacy monthly build (`GeneSetTools/create_genesets_release.sh`), and what it shows about how the gene sets have changed since 2021.

**Short answer to "are we losing GO gene sets?"** The pipeline isn't losing them. The decline comes from GO itself. But every GOALL file has carried about 1,300 duplicate GO IDs since October 2021, and a one-line change fixes that.

---

## Contents

1. [Why a new workflow](#why-a-new-workflow)
2. [How it is organised](#how-it-is-organised)
3. [Running it](#running-it)
4. [Findings](#findings)
   - [GO gene set decline](#1-go-gene-set-decline)
   - [Duplicate GO IDs (issue #22)](#2-duplicate-go-ids-issue-22)
   - [Identifier mapping benchmark](#3-identifier-mapping-benchmark)
   - [Cross-species conversion](#4-cross-species-conversion)
   - [Static sources and identifier decay](#5-static-sources-and-identifier-decay)
   - [Failure isolation](#6-failure-isolation)
5. [Recommendations](#recommendations)
6. [Still to do](#still-to-do)
7. [Caveats](#caveats)

---

## Why a new workflow

The legacy build is one ~2,150-line Bash script that processes every source in a fixed order. When one source breaks, the script keeps going and publishes an incomplete release, and the only fix is to rerun the whole ~18-hour build. Nothing checks the outputs automatically, so problems such as the GO duplicates were only found by people reading the files.

The new workflow:

- runs **one job per source and file**, so a failure stays inside that source;
- **reruns only what is missing** after a fix;
- records a **SHA-256 checksum, URL, download time and `Last-Modified` date** for every downloaded file;
- reads every source from one registry file, `sources.yaml`.

It doesn't replace the legacy build yet. GO is the first source fully ported (see [Still to do](#still-to-do)).

## How it is organised

```
pipeline/
  sources.yaml          one entry per source: URL, format, native ID, parser, live/static
  config.yaml           GO releases, Ensembl release, HGNC months to analyse
  Snakefile             the workflow
  scripts/
    go_build.py             Python port of GOGeneSetFileMaker.java + Ontology.java
    compare_gmt.py          per-gene-set Jaccard parity check
    fetch_url.py            download with retries + checksum
    mapping_benchmark.py    Aim 2.1
    orthology_benchmark.py  Aim 2.1
    go_funnel_report.py     Aim 2.2
    release_history.py      Aim 2.2 (Bader Lab archive)
    source_status.py        Aim 2.3
  tests/test_go_build.py
  results/aim2/         tables (.tsv) and figures (.png)
```

**The GO module matches the legacy Java tool exactly.** On GO release 2026-08-05, all 21,466 no-IEA gene sets are identical (Jaccard = 1) for both UniProt and symbol output.

## Running it

```bash
cd pipeline
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m pytest tests
```

| Command | What it does | When |
|---|---|---|
| `snakemake -c4 --keep-going monthly_raw` | Download every live source and build GO | any time |
| `snakemake -c4 go_funnel` | Rebuild GO from 17 archived GO releases and count gene sets after each step | any time |
| `snakemake -c4 mapping_benchmark` | Compare four ID-mapping methods | any time |
| `snakemake -c1 orthology_benchmark` | HomoloGene vs Ensembl Compara for mouse and rat | any time |
| `snakemake -c1 source_status` | Classify sources and measure identifier decay | monthly |
| `snakemake -c1 release_history` | Summarise every published release on download.baderlab.org | **9 pm-8 am ET only** |

> **Download window.** The Bader Lab download server asks for downloads between 9 pm and 8 am Eastern. `release_history` refuses to start outside that window, stops when it closes, and picks up where it left off the next night.

---

## Findings

### 1. GO gene set decline

**Question:** Unique GO gene sets in the published files fell between 2021 and 2026. Is that GO curation or our pipeline?

**Method:** I rebuilt GO gene sets from 17 archived GO releases (January 2021 to August 2026) with the legacy logic, counting the gene sets left after each step. I compared the counts with a reference that keeps only what GO itself defines: each branch's own, non-obsolete terms.

![GO gene sets per release](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/go_funnel/go_genesets_over_time.png?raw=true)

**Result:** In every release, the legacy build's unique GO IDs (blue) track GO's own count (green) to within 0.31%.

| Jan 2021 to Aug 2026 | No IEA | With IEA |
|---|---|---|
| GO itself (reference) | -1.1% | -5.6% |
| Legacy build, unique GO IDs | -1.2% | -5.6% |
| Legacy build, GOALL **lines** | -2.9% | -7.2% |

**The decline is GO curation, not the pipeline.** The bigger drop you see when counting lines (orange) comes from the duplicates described below. The September 2024 fix that removes NOT-qualified annotations changes the count by only about 40 gene sets.

This chart shows where in the processing the change happens:

![Change per processing stage](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/go_funnel/go_stage_change.png?raw=true)

Annotations grew by 50%, but GO retired 13.6% of its live terms, so annotations now sit on fewer terms.

Data: [`go_stage_change.tsv`](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/go_funnel/go_stage_change.tsv), [`go_funnel_counts.tsv`](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/go_funnel/go_funnel_counts.tsv)

### 2. Duplicate GO IDs (issue #22)

**Cause.** When the build propagates genes up the ontology, it follows both `is_a` and `part_of` links, including `part_of` links that cross from one GO branch to another. So when the molecular function (MF) file is built, biological process (BP) terms that an MF term is `part_of` get written into the MF file. GOALL concatenates the BP, MF and CC files, so those terms appear twice, with different gene lists.

**Why it started in October 2021.** The code has done this since 2012. In May 2021, commit `f13239b` switched the ontology file from `gene_ontology.1_2.obo` to the full `go.obo`, which contains the cross-branch links. Those months fall in the archive gap, so October 2021 is the first published release that shows the duplicates.

![Duplicate GO IDs](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/go_funnel/go_duplicates_over_time.png?raw=true)

**Fix.** Running the **same legacy code with `go-basic.obo`** (the GO edition without cross-branch links) gives **0 duplicates in every release**. Its counts match the GO reference to within 15 gene sets. In `create_genesets_release.sh`, that means downloading `go-basic.obo` instead of `go.obo`.

| Aug 2026 | No IEA | With IEA |
|---|---|---|
| Duplicates with `go.obo` (current) | 1,282 | 1,330 |
| Duplicates with `go-basic.obo` | 0 | 0 |

The same count appears in the live EBI files built this month (Nov 2026): 1,274 / 1,319 duplicates.

### 3. Identifier mapping benchmark

Four methods were compared:

| Method | What it is |
|---|---|
| **legacy** | UniProt ID-mapping REST, as GenesetTools does it (symbol to UniProt with taxId 9606 to Entrez) |
| **bridgedb** | BridgeDb web service (its data: Ensembl 111, built Oct 2024) |
| **ensembl** | Ensembl release 116 mapping files (BioMart was down on 18 Nov 2026) |
| **gprofiler** | g:Profiler g:Convert |

**Test A: UniProt to Entrez** on all 19,776 UniProt accessions in the human GO annotation file, scored against HGNC.

![UniProt mapping](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/mapping/mapping_uniprot.png?raw=true)

| Method | Coverage | Agrees with HGNC | One-to-many |
|---|---|---|---|
| legacy | 95.0% | **95.4%** | **0.5%** |
| ensembl | 94.8% | 94.2% | 1.4% |
| bridgedb | 94.2% | 93.9% | 1.3% |
| gprofiler | 94.5% | 91.8% | 3.7% |

**Test B: symbol to Entrez** on a labelled set of 200 symbols, 50 per category, saved as a regression test in [`symbol_testset.tsv`](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/mapping/symbol_testset.tsv).

![Symbol test set](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/mapping/mapping_symbol_testset.png?raw=true)

| % correct | Unambiguous | Renamed (previous symbol) | Shared alias (should not guess) | Withdrawn (should not map) |
|---|---|---|---|---|
| legacy | 98 | **76** | 78 | 100 |
| bridgedb | 94 | 0 | 100 | 100 |
| gprofiler | 94 | 0 | 92 | 100 |

**Takeaways:**

- The legacy UniProt route is the most accurate method for UniProt IDs, the native ID of GO, Reactome and most BioPAX sources.
- It is the only method that resolves **renamed** genes. BridgeDb and g:Profiler don't map previous symbols at all, which matters for old static sources such as KEGG (2011).
- But the legacy route **silently picks one gene for 22% of shared aliases**, so a gene set can quietly gain the wrong gene.
- Its weak point is reproducibility: UniProt's mapping changes every release, and the build doesn't record which release it used.

### 4. Cross-species conversion

Mouse and rat gene sets are converted from human with **HomoloGene**, which NCBI stopped updating in 2014. This test compares it with current **Ensembl Compara** orthologues, measured on human protein-coding genes. It also checks the woodchuck homology table from 2021.

> *Results pending. The Ensembl Compara download was still running when this page was written. They will be in `results/aim2/orthology/orthology_summary.tsv` and `orthology.png`.*

### 5. Static sources and identifier decay

Every source in `sources.yaml` is classified each month:

| Class | Rule | What the build does |
|---|---|---|
| **active** | default | download and process in full |
| **static** | content unchanged for 12 months | reuse the archived file; re-translate IDs only |
| **unavailable** | download failed in 2 consecutive months | carry the last good version forward and alert maintainers |

Any change of class is flagged `needs_review` for a maintainer to confirm. Current classes: [`source_status.tsv`](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/status/source_status.tsv).

**Identifier decay** is the share of a static source's genes that no longer match an approved HGNC identifier, measured against HGNC monthly snapshots from 2021 to 2026.

![Identifier decay](https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild/blob/feature/reena-bugfixes/pipeline/results/aim2/status/decay.png?raw=true)

| Source (frozen since) | ID type | Genes | Not current (Oct 2026) | After previous-symbol rescue |
|---|---|---|---|---|
| KEGG (July 2011) | symbol | 5,963 | 5.2% | 0.8% |
| NCI-Nature (2014) | UniProt | 2,571 | 1.9% | n/a |
| IOB (July 2011) | Entrez | 861 | 0.1% | n/a |

Symbols decay much faster than stable IDs, which supports storing stable identifiers alongside symbols. For KEGG, looking up unambiguous previous symbols would recover most of the lost genes.

### 6. Failure isolation

To test failure isolation, Panther's download URL was broken on purpose:

- Panther failed after 3 tries.
- **The other 11 downloads and the GO build all completed.**
- After the URL was restored, the rerun executed **only** the Panther job.

The test also caught a bug in the new GO module. A compressed file saved without a `.gz` ending was read as text and produced 0 gene sets with no error, the same silent-empty failure the legacy build has. Fixed: compression is now detected from the file's contents, and `go_build.py` exits with an error if it writes no gene sets.

---

## Recommendations

1. **Download `go-basic.obo` instead of `go.obo`** in `create_genesets_release.sh`. This removes all ~1,300 duplicate GO IDs (issue #22) without changing any other gene set.
2. **Keep the UniProt mapping, but record its version** (UniProt release and date) with every release, and save the mapping table used so a release can be rebuilt.
3. **Flag shared aliases** instead of letting the translator pick one gene.
4. **Rescue renamed symbols in static sources** through HGNC previous symbols before translation.
5. **Count unique gene set IDs, not lines**, in summary reports and monitoring.

## Still to do

- Port the BioPAX sources (Reactome, Panther, PathBank, HumanCyc, NCI, NetPath, IOB), HPO and DrugBank. They are still parsed by the legacy Java/Perl tools (`*_legacy` in `sources.yaml`).
- Move ID translation (UniProt to Entrez / symbol / Ensembl) and mouse/rat/woodchuck conversion into the workflow.
- Provenance manifest per GMT file (Aim 1.3). The `.download.json` files already hold the URL, time, `Last-Modified` and SHA-256.
- Run `release_history` overnight to measure what each *published* release contained and to detect change points.
- Finish the orthology benchmark (section 4).

## Caveats

- The archived GO annotation files come from the GO Consortium (release.geneontology.org). They are copies of the EBI GOA files the live build downloads, so counts may differ slightly from what the build saw at the time.
- `gene_ontology.1_2.obo` isn't archived, so the duplicate timing rests on the code history plus the `go-basic.obo` test, not on a rebuild with the original file.
- Ensembl BioMart was unreachable on 18 Nov 2026, so Ensembl results use the release 116 download files.
- Duplicate counts here come from rebuilds and may differ slightly from the published files (1,296 / 1,344 in April 2026).
