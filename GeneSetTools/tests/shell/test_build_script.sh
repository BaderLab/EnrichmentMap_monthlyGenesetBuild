#!/bin/bash
#
# Tests for create_genesets_release.sh.
#
# 1. Unit tests: individual shell functions are loaded from the script and run
#    against the fake curl / fake GenesetTools.
# 2. Full builds: the whole script is run in a sandbox (tests/shell/fakes/
#    make_sandbox.py) with fake downloads, a fake GenesetTools.jar and fake
#    static files, and its outputs are checked. One normal run and two runs with
#    injected failures.
#
# Set KEEP_TEST_DIRS=1 to keep the sandboxes for inspection.

HERE=$(cd "$(dirname "$0")" && pwd)
TOOLS=$(cd "$HERE/../.." && pwd)
SCRIPT="$TOOLS/create_genesets_release.sh"
source "$HERE/../lib/assert.sh"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/genesets_shell_tests.XXXXXX")
if [ -z "$KEEP_TEST_DIRS" ]; then
	trap 'rm -rf "$WORK"' EXIT
else
	echo "test sandboxes kept in $WORK"
fi
DATE=$(date '+%B_%d_%Y')

#---------------------------------------------------------------------------
# helpers
#---------------------------------------------------------------------------

new_sandbox() { # name -> prints sandbox path
	local s="$WORK/$1"
	mkdir -p "$s"
	python3 "$HERE/fakes/make_sandbox.py" "$s" "$TOOLS" || { echo "could not build sandbox $s"; exit 1; }
	echo "$s"
}

# run the whole build in a sandbox. Extra VAR=value arguments are passed as environment.
run_build() {
	local s=$1; shift
	(
		cd "$s/tool" && env PATH="$s/bin:$PATH" FAKE_INTERNET="$s/internet" FAKE_CURL_LOG="$s/curl.log" \
			FAKE_SLACK_LOG="$s/slack.log" FAKE_JAVA_LOG="$s/java.log" PUBLISH_DIR="$s/publish" "$@" \
			bash ./create_genesets_release.sh > "$s/build.log" 2>&1
	)
	echo $? > "$s/exit_code"
}

# define one function from the build script in this shell
load_function() {
	local body
	body=$(awk -v n="$1" '$0 ~ "^function "n" *\\{" {p=1} p {print} p && /^}/ {exit}' "$SCRIPT")
	if [ -z "$body" ]; then
		# keep going so every test that needs it is reported as a failure
		echo "WARNING: function $1 is not in $SCRIPT"
		eval "function $1 { echo \"function $1 is missing from the build script\"; return 99; }"
		return
	fi
	eval "$body"
}

for f in download_file check_scraped_filename download_wikipathways_data download_pfocr_data \
		get_webfile_version remove_slashes_from_gmt process_biopax_novalidation \
		check_release_outputs translate_uniprot_rest; do
	load_function $f
done

# environment for running single functions against the fake internet
UNIT=$(new_sandbox unit)
export PATH="$UNIT/bin:$PATH" FAKE_INTERNET="$UNIT/internet" FAKE_CURL_LOG="$UNIT/curl.log" \
	FAKE_SLACK_LOG="$UNIT/slack.log" FAKE_JAVA_LOG="$UNIT/java.log"
TOOLDIR="$UNIT/tool"

#---------------------------------------------------------------------------
# unit tests
#---------------------------------------------------------------------------

start_test "download_file saves the file and returns 0 when the download works (#24)"
d="$WORK/u_download"; mkdir -p "$d"
download_file "https://reactome.org/ContentService/data/database/version" "$d/version" "Reactome" > "$d/out" 2>&1
assert_eq 0 $? "return status"
assert_eq "95" "$(cat "$d/version")" "downloaded content"
assert_not_contains "$d/out" "ERROR"

start_test "download_file reports a 404 and doesn't save the error page (#24)"
echo "old partial data" > "$d/missing.tar.gz"
download_file "https://data.pantherdb.org/ftp/pathway/current_release/NOPE.tar.gz" "$d/missing.tar.gz" "Panther" > "$d/out" 2>&1
assert_eq 1 $? "return status for a 404"
assert_contains "$d/out" "ERROR: Panther download failed (https://data.pantherdb.org/ftp/pathway/current_release/NOPE.tar.gz)"
assert_file_missing "$d/missing.tar.gz" "a failed download must not leave a (partial or error page) file behind"
assert_contains "$UNIT/curl.log" "NOPE.tar.gz"
assert_true "download_file must use curl --fail" grep -qE "^GET	[A-Za-z]*f[A-Za-z]* .*NOPE.tar.gz" "$UNIT/curl.log"

start_test "check_scraped_filename accepts exactly one file name (#25)"
check_scraped_filename "wikipathways-20261010-gmt-Homo_sapiens.gmt" "WikiPathways" "Homo_sapiens" > "$d/out" 2>&1
assert_eq 0 $? "one name"
check_scraped_filename "" "PFOCR" "Mus_musculus" > "$d/out" 2>&1
assert_eq 1 $? "no name"
assert_contains "$d/out" "ERROR: could not find a PFOCR file for Mus_musculus"
check_scraped_filename "$(printf 'a-Homo_sapiens.gmt\nb-Homo_sapiens.gmt')" "PFOCR" "Homo_sapiens" > "$d/out" 2>&1
assert_eq 1 $? "two names"
assert_contains "$d/out" "ERROR: found more than one PFOCR file for Homo_sapiens"

start_test "download_pfocr_data gives Mouse the human (non-chemical) file, as before (#25 - behaviour kept until decided)"
VERSIONS="$WORK/u_versions"; PFOCR="$WORK/u_pfocr"; mkdir -p "$VERSIONS" "$PFOCR"
download_pfocr_data Mus_musculus > "$d/out" 2>&1
assert_eq 0 $? "return status"
assert_file_nonempty "$PFOCR/PFOCR_Mus_musculus_entrezgene.gmt"
assert_contains "$PFOCR/PFOCR_Mus_musculus_entrezgene.gmt" "PFOCR Homo_sapiens gmt figure"
assert_not_contains "$PFOCR/PFOCR_Mus_musculus_entrezgene.gmt" "chemical"
assert_contains "$VERSIONS/PFOCR.txt" "pfocr-20250701-gmt-Homo_sapiens.gmt"

start_test "download_pfocr_data stops when the file name can't be found on the page (#25)"
mv "$UNIT/internet/data.wikipathways.org/pfocr/current/index.html" "$WORK/pfocr_index.bak"
echo "<html><body>moved</body></html>" > "$UNIT/internet/data.wikipathways.org/pfocr/current/index.html"
rm -f "$PFOCR"/*; : > "$UNIT/curl.log"
download_pfocr_data Homo_sapiens > "$d/out" 2>&1
assert_eq 1 $? "return status"
assert_contains "$d/out" "ERROR: could not find a PFOCR file for Homo_sapiens"
assert_file_missing "$PFOCR/PFOCR_Homo_sapiens_entrezgene.gmt"
assert_no_match "$UNIT/curl.log" "pfocr/current/+	[^-]" "must not download the bare directory URL as the data file"
mv "$WORK/pfocr_index.bak" "$UNIT/internet/data.wikipathways.org/pfocr/current/index.html"

start_test "download_wikipathways_data stops when the species isn't on the page (#25)"
WIKIPATHWAYS="$WORK/u_wp"; mkdir -p "$WIKIPATHWAYS"
download_wikipathways_data Bos_taurus > "$d/out" 2>&1
assert_eq 1 $? "return status"
assert_contains "$d/out" "ERROR: could not find a WikiPathways file for Bos_taurus"
assert_file_missing "$WIKIPATHWAYS/WikiPathways_Bos_taurus_entrezgene.gmt"
download_wikipathways_data Rattus_norvegicus > "$d/out" 2>&1
assert_eq 0 $? "return status for a species that exists"
assert_contains "$WIKIPATHWAYS/WikiPathways_Rattus_norvegicus_entrezgene.gmt" "WP_Rattus_norvegicus pathway"

start_test "remove_slashes_from_gmt rewrites a real gmt and never creates an empty one (#34)"
g="$WORK/u_gmt"; mkdir -p "$g"; cd "$g"
printf 'a/b pathway%%Src%%1\tdesc/x\tP1\n' > real.gmt
remove_slashes_from_gmt real.gmt real.owl > out 2>&1
assert_eq "$(printf 'a b pathway%%Src%%1\tdesc x\tP1')" "$(cat real.gmt)" "slashes replaced"
assert_not_contains out "ERROR"
remove_slashes_from_gmt missing.gmt missing.owl > out 2>&1
assert_file_missing missing.gmt "no gmt may be created when there was none"
assert_contains out "ERROR: no gmt made from missing.owl"
: > empty.gmt
remove_slashes_from_gmt empty.gmt empty.owl > out 2>&1
assert_contains out "ERROR: no gmt made from empty.owl"
assert_file_missing temp.txt "temp.txt must not be left behind"

start_test "process_biopax_novalidation leaves no gmt when GenesetTools fails (#34)"
echo "<owl/>" > bad.owl; echo "<owl/>" > good.owl
FAKE_FAIL_TOGSEA=bad.owl process_biopax_novalidation bad.owl "UniProt" "Reactome" "10116" > out 2>&1
assert_file_missing "bad.owl_UniProt.gmt" "failed conversion must not produce a gmt"
assert_contains out "ERROR: no gmt made from bad.owl"
process_biopax_novalidation good.owl "UniProt" "Reactome" "10116" > out 2>&1
assert_file_nonempty "good.owl_UniProt.gmt"
assert_not_contains "good.owl_UniProt.gmt" "/" "slashes removed from the real gmt"
cd "$HERE"

start_test "check_release_outputs (#23)"
r="$WORK/u_release"
for sp in Human Mouse; do mkdir -p "$r/$sp/entrezgene"; echo "set	d	1" > "$r/$sp/entrezgene/${sp}_AllPathways_x_entrezgene.gmt"; done
check_release_outputs "$r" Human Mouse > "$d/out" 2>&1
assert_eq 0 $? "complete release"
: > "$r/Mouse/entrezgene/Mouse_GO_AllPathways_y_entrezgene.gmt"
check_release_outputs "$r" Human Mouse > "$d/out" 2>&1
assert_eq 1 $? "release with an empty AllPathways file"
assert_contains "$d/out" "ERROR: $r/Mouse/entrezgene/Mouse_GO_AllPathways_y_entrezgene.gmt is missing or empty"
check_release_outputs "$r" Human Rat > "$d/out" 2>&1
assert_eq 1 $? "release with a species missing"
assert_contains "$d/out" "ERROR: no AllPathways files were created for Rat"

start_test "translate_uniprot_rest reports a failed translation in the build log (#39)"
cd "$g"; printf 'set%%S%%1\tset\tP04637\n' > Human_test_UniProt.gmt
FAKE_FAIL_TRANSLATE=Human_test translate_uniprot_rest Human_test_UniProt.gmt 9606 UniProt symbol > out 2>&1
assert_contains out "ERROR: translating Human_test_UniProt.gmt from UniProt to symbol failed"
translate_uniprot_rest Human_test_UniProt.gmt 9606 UniProt symbol > out 2>&1
assert_not_contains out "ERROR"
assert_file_nonempty Human_test_symbol.gmt
cd "$HERE"

#---------------------------------------------------------------------------
# full build - everything works
#---------------------------------------------------------------------------
echo
echo "running the full build in a sandbox (normal run)..."
S=$(new_sandbox full)
run_build "$S"
R="$S/tool/$DATE"
LOG="$S/build.log"

start_test "normal build finishes and publishes (#23)"
assert_eq 0 "$(cat "$S/exit_code")" "build exit status (see $LOG)"
assert_eq "$DATE/" "$(readlink "$S/publish/current_release")" "current_release points at the new release"
for sp in Human Mouse Rat Woodchuck; do
	assert_file_exists "$S/publish/$DATE/$sp" "published species folder"
done
assert_contains "$S/slack.log" "[data build complete"
assert_not_contains "$LOG" "ERROR" "a normal build logs no errors"

start_test "no 'command not found' in the build log (#29)"
assert_not_contains "$LOG" "command not found"

start_test "every download uses curl --fail (#24)"
# every GET that saves to a file must have -f
assert_no_match "$S/curl.log" "^GET	[A-Za-eg-z]* [^	]*	[^	]*	[^-]" "a download without -f/--fail"
assert_true "downloads were logged" grep -q "BioPAX.tar.gz" "$S/curl.log"

start_test "Rat WikiPathways go to SRC_Rat/WikiPathways (#26)"
assert_file_nonempty "$R/SRC_Rat/WikiPathways/WikiPathways_Rattus_norvegicus_entrezgene.gmt"
assert_file_nonempty "$R/Rat/entrezgene/Pathways/Rat_WikiPathways_${DATE}_Entrezgene.gmt"
assert_contains "$R/Rat/entrezgene/Rat_AllPathways_${DATE}_entrezgene.gmt" "WP_Rattus_norvegicus pathway"

start_test "Mouse GO + AllPathways files contain the pathways and have the right names (#27)"
for id in symbol ensembl entrezgene UniProt; do
	for pf in withPFOCR noPFOCR; do
		for iea in with_GO_iea no_GO_iea; do
			f="$R/Mouse/$id/Mouse_GO_AllPathways_${pf}_${iea}_${DATE}_${id}.gmt"
			assert_file_nonempty "$f"
			assert_contains "$f" "WP_Mus_musculus pathway" "pathways in $(basename "$f")"
			assert_contains "$f" "GO bp term" "GO in $(basename "$f")"
		done
	done
done
assert_true "no Mouse file names with a missing '_'" bash -c "! find '$R/Mouse' -name '*PFOCRno_GO*' -o -name '*PFOCRwith_GO*' -o -name '*PFOCR${DATE}*' | grep -q ."

start_test "Mouse ensembl stats are computed from the Mouse ensembl folder (#28)"
assert_file_nonempty "$R/Mouse/Summary_GeneSet_Counts_ensembl.txt"
assert_contains "$R/Mouse/Summary_GeneSet_Counts_ensembl.txt" "MOUSE_GO_bp_with_GO_iea_ensembl.gmt"
assert_file_exists "$R/Mouse/Build_summary_stats_ensembl.log"

start_test "Woodchuck GOBP file is spelled 'Human' (#30)"
assert_file_nonempty "$R/Woodchuck/symbol/Woodchuck_Human_GOBP_AllPathways_with_GO_iea_${DATE}_symbol.gmt"
assert_true "no 'Huamn' file names" bash -c "! find '$R' -name '*Huamn*' | grep -q ."

start_test "Woodchuck translation summary is created (#31)"
f="$R/Woodchuck/entrezgene/Pathways/Woodchuck_translatedPathways_Entrezgene_translation_summary.log"
assert_file_nonempty "$f"
assert_contains "$f" "converted to taxid	9995"

start_test "Rat Reactome passes taxid 10116 (#32)"
assert_matches "$S/java.log" "^toGSEA	--biopax Rattus_norvegicus.owl .*--species 10116	"
assert_no_match "$S/java.log" "^toGSEA	.*--species *	" "toGSEA called without a species"

start_test "HumanCyc, NCI and NetPath versions are saved (#33)"
for sp in Human Mouse Rat Woodchuck; do
	v="$R/$sp/${DATE}_versions.txt"
	assert_contains "$v" "biocyc pathways were downloaded on August 1, 2025" "HumanCyc version in $sp"
	assert_contains "$v" "NCI_Nature pathways were downloaded" "NCI version in $sp"
	assert_contains "$v" "NetPath pathways come from a static copy" "NetPath version in $sp"
done
assert_not_contains "$LOG" "humancyc.txt: No such file"
assert_not_contains "$LOG" "NCI_Nature.txt: No such file"
assert_not_contains "$LOG" "NetPath.txt: No such file"

start_test "no empty GMT files are produced from BioPAX (#34)"
assert_true "no empty gmt files in SRC" bash -c "! find '$R/SRC' -name '*.gmt' -size 0 | grep -q ."
assert_not_contains "$R/SRC/Reactome/Homosapiens.owl_UniProt.gmt" "/"

start_test "Rat go.obo is downloaded over https (#35)"
assert_contains "$S/curl.log" "https://current.geneontology.org/ontology/go.obo"
assert_not_contains "$S/curl.log" "http://current.geneontology.org"

start_test "no hard-coded logins in any script (#36)"
# a literal user:password after curl -u (reading it from a file, -u "$(cat ...)", is fine)
hardcoded=$(grep -rnE --include='*.sh' --include='*.py' --include='*.pl' --include='*.R' --include='*.Rmd' \
	-e "-u +[A-Za-z0-9._@+-]+:[^ ]+" "$TOOLS" | grep -v "/tests/")
assert_eq "" "$hardcoded" "hard-coded curl -u user:password"
assert_not_contains "$S/curl.log" "data-20541"
assert_not_contains "$S/curl.log" "emililab"

start_test "Slack posts use Content-type application/json (#43)"
assert_true "slack messages were sent" test -s "$S/slack.log"
assert_no_match "$S/slack.log" "Content-type: plication/json"

start_test "Mouse PFOCR is still the human file (#25 - kept until decided)"
assert_contains "$R/SRC_Mouse/PFOCR/PFOCR_Mus_musculus_entrezgene.gmt" "PFOCR Homo_sapiens gmt figure"

#---------------------------------------------------------------------------
# full build - a species ends up with empty combined files
#---------------------------------------------------------------------------
echo
echo "running the full build in a sandbox (all human->other species conversions fail)..."
S=$(new_sandbox broken)
run_build "$S" FAKE_FAIL_CONVERT=Human
R="$S/tool/$DATE"
LOG="$S/build.log"

start_test "an incomplete release is not published and current_release is unchanged (#23)"
assert_eq 1 "$(cat "$S/exit_code")" "build exit status"
assert_eq "October_01_2026/" "$(readlink "$S/publish/current_release")" "current_release still points at the previous release"
assert_file_missing "$S/publish/$DATE" "the incomplete release must not be copied to the web server"
assert_contains "$LOG" "ERROR: release $DATE is incomplete - not publishing it"
assert_matches "$LOG" "ERROR: .*/Woodchuck/entrezgene/Woodchuck_AllPathways_${DATE}_entrezgene.gmt is missing or empty"
assert_contains "$S/slack.log" "ERROR: release $DATE is incomplete - not published"
assert_not_contains "$S/slack.log" "[data build complete"

#---------------------------------------------------------------------------
# full build - downloads, parsing and translations fail
#---------------------------------------------------------------------------
echo
echo "running the full build in a sandbox (downloads, BioPAX parsing and a translation fail)..."
S=$(new_sandbox failures)
rm "$S/internet/data.pantherdb.org/ftp/pathway/current_release/BioPAX.tar.gz"
python3 - "$S/internet/data.wikipathways.org/current/gmt/index.html" <<'EOF'
import sys
p = sys.argv[1]
lines = [l for l in open(p) if "Homo_sapiens" not in l]
open(p, "w").writelines(lines)
EOF
run_build "$S" FAKE_FAIL_TOGSEA=NCI-Nature FAKE_FAIL_TRANSLATE=KEGG
R="$S/tool/$DATE"
LOG="$S/build.log"

start_test "a failed download is reported and no error page is saved as data (#24)"
assert_contains "$LOG" "ERROR: Panther download failed"
assert_file_missing "$R/SRC/Panther/BioPAX.tar.gz" "the 404 page must not be saved as BioPAX.tar.gz"

start_test "a missing WikiPathways file name is reported (#25)"
assert_contains "$LOG" "ERROR: could not find a WikiPathways file for Homo_sapiens"
assert_file_missing "$R/SRC/WikiPathways/WikiPathways_Homo_sapiens_entrezgene.gmt"

start_test "a failed BioPAX conversion is reported and leaves no empty gmt (#34)"
assert_contains "$LOG" "ERROR: no gmt made from NCI-Nature_Curated.bp3.owl"
assert_true "no empty gmt in SRC/NCI" bash -c "! find '$R/SRC/NCI' -name '*.gmt' -size 0 | grep -q ."
assert_file_missing "$R/SRC/NCI/NCI-Nature_Curated.bp3.owl_UniProt.gmt"

start_test "a failed translation is reported in the build log (#39)"
assert_contains "$LOG" "ERROR: translating July2011_Human_KEGG_symbol.gmt from symbol to UniProt failed"

finish_tests
