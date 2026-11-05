#!/bin/bash
#
# Run all the GeneSetTools tests:
#   tests/shell/test_build_script.sh        create_genesets_release.sh (sandboxed full builds)
#   tests/shell/test_cron_and_summaries.sh  cron jobs + create_summaries.sh
#   tests/java/run_java_tests.sh            GenesetTools Java code (fake UniProt server)
#   tests/r/test_geneset_tracking.R         the summary report (Geneset_tracking.Rmd)
#
# Exits 0 only if every suite ran and passed. A suite whose tools aren't
# installed (e.g. no R) is reported as SKIPPED and makes the run fail, unless
# ALLOW_SKIP=1 is set.

HERE=$(cd "$(dirname "$0")" && pwd)
failed=()
skipped=()
passed=()

run_suite() { # name required-command command...
	local name=$1 needs=$2
	shift 2
	echo
	echo "=================================================================="
	echo "  $name"
	echo "=================================================================="
	for c in $needs; do
		if ! command -v "$c" > /dev/null ; then
			echo "SKIPPED: $c is not installed"
			skipped+=("$name ($c not installed)")
			return
		fi
	done
	if "$@" ; then
		passed+=("$name")
	else
		failed+=("$name")
	fi
}

run_suite "build script (create_genesets_release.sh)" "python3" bash "$HERE/shell/test_build_script.sh"
run_suite "cron jobs and create_summaries.sh" "python3" bash "$HERE/shell/test_cron_and_summaries.sh"
run_suite "Java (GenesetTools)" "javac java" bash "$HERE/java/run_java_tests.sh"
run_suite "summary report (Geneset_tracking.Rmd)" "Rscript python3" Rscript "$HERE/r/test_geneset_tracking.R"

echo
echo "=================================================================="
for s in "${passed[@]}"; do echo "PASSED   $s"; done
for s in "${skipped[@]}"; do echo "SKIPPED  $s"; done
for s in "${failed[@]}"; do echo "FAILED   $s"; done

if [ ${#failed[@]} -gt 0 ]; then
	exit 1
fi
if [ ${#skipped[@]} -gt 0 ] && [ -z "$ALLOW_SKIP" ]; then
	echo "(some suites were skipped - set ALLOW_SKIP=1 to accept that)"
	exit 1
fi
exit 0
