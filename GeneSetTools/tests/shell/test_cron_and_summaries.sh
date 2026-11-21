#!/bin/bash
#
# Tests for the cron scripts (Slack messages, #43) and
# geneset_tracking/create_summaries.sh (README only replaced on success, #42).
# docker, curl and perl are replaced with stand-ins; the scripts under test are
# copied into a temporary folder.

HERE=$(cd "$(dirname "$0")" && pwd)
TOOLS=$(cd "$HERE/../.." && pwd)
source "$HERE/../lib/assert.sh"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/genesets_cron_tests.XXXXXX")
if [ -z "$KEEP_TEST_DIRS" ]; then
	trap 'rm -rf "$WORK"' EXIT
else
	echo "test folders kept in $WORK"
fi
MONTH=$(date '+%B_%Y')

# stand-ins
BIN="$WORK/bin"
mkdir -p "$BIN"
cat > "$BIN/curl" <<EOF
#!/bin/bash
exec python3 "$HERE/fakes/fake_curl.py" "\$@"
EOF
# fake docker: 'renders' the report by writing the requested output html.
#  FAKE_DOCKER_FAIL=<species>   R fails for that species (error on stderr, exit 1)
#  FAKE_DOCKER_EMPTY=<species>  exits 0 but writes an empty html
cat > "$BIN/docker" <<'EOF'
#!/bin/bash
all="$*"
species=$(echo "$all" | sed -n "s/.*species='\([A-Za-z]*\)'.*/\1/p")
out=$(echo "$all" | sed -n "s/.*output_file='\/home\/rstudio\/projects\/\([^']*\)'.*/\1/p")
echo "processing file: Geneset_tracking.Rmd ($species)"
if [ "$FAKE_DOCKER_FAIL" = "$species" ]; then
	echo "Error in merge(stats_summary_table, stats): fake R failure for $species" >&2
	echo "Execution halted" >&2
	exit 1
fi
if [ "$FAKE_DOCKER_EMPTY" = "$species" ]; then
	: > "$out"
	exit 0
fi
echo "<html>new report for $species</html>" > "$out"
EOF
# local::lib isn't needed by the stand-ins
cat > "$BIN/perl" <<'EOF'
#!/bin/bash
exit 0
EOF
chmod +x "$BIN"/*

#---------------------------------------------------------------------------
# create_summaries.sh (#42)
#---------------------------------------------------------------------------

setup_summaries() { # dir
	local d=$1
	mkdir -p "$d/release"
	cp "$TOOLS/geneset_tracking/create_summaries.sh" "$d/"
	for sp in Human Mouse Rat; do
		mkdir -p "$d/release/$sp"
		echo "<html>OLD $sp report</html>" > "$d/release/$sp/README.html"
	done
}

run_summaries() { # dir env...
	local d=$1; shift
	( cd "$d" && env PATH="$BIN:$PATH" RELEASE_DIR="$d/release" "$@" bash ./create_summaries.sh > "$d/stdout.txt" 2>&1 )
	echo $? > "$d/exit_code"
}

start_test "all three reports render: every README is replaced (#42)"
d="$WORK/summaries_ok"; setup_summaries "$d"
run_summaries "$d"
assert_eq 0 "$(cat "$d/exit_code")" "exit status"
for sp in Human Mouse Rat; do
	assert_contains "$d/release/$sp/README.html" "new report for $sp"
done
assert_not_contains "$d/stdout.txt" "ERROR"

start_test "a failed render leaves that README alone and keeps R's error message (#42)"
d="$WORK/summaries_fail"; setup_summaries "$d"
lower_html="$d/Geneset_tracking_mouse_${MONTH}.html"
echo "<html>STALE mouse report from an earlier run this month</html>" > "$lower_html"
run_summaries "$d" FAKE_DOCKER_FAIL=Mouse FAKE_DOCKER_EMPTY=Rat
assert_eq 1 "$(cat "$d/exit_code")" "exit status when a report fails"
assert_contains "$d/release/Human/README.html" "new report for Human"
assert_contains "$d/release/Mouse/README.html" "OLD Mouse report" "Mouse README must not be replaced (not by the stale file either)"
assert_contains "$d/release/Rat/README.html" "OLD Rat report" "Rat README must not be replaced by an empty report"
assert_contains "$d/stdout.txt" "ERROR: Mouse summary failed, README not updated"
assert_contains "$d/stdout.txt" "ERROR: Rat summary failed, README not updated"
assert_not_contains "$d/stdout.txt" "ERROR: Human"
assert_contains "$d/processing_output_Mouse_${MONTH}.txt" "fake R failure for Mouse" "R's stderr must be saved"
assert_contains "$d/processing_output_Mouse_${MONTH}.txt" "Execution halted"

#---------------------------------------------------------------------------
# create_genesets_cronjob.sh (#43)
#---------------------------------------------------------------------------

setup_cron() { # dir build-exit-status
	local home=$1
	local tools="$home/EnrichmentMap_monthlyGenesetBuild/GeneSetTools"
	mkdir -p "$tools"
	cp "$TOOLS/create_genesets_cronjob.sh" "$tools/"
	echo "https://hooks.slack.test/services/T000/B000/XXXX" > "$tools/slack_webhook"
	printf '#!/bin/bash\necho "fake build"\nexit %s\n' "$2" > "$tools/create_genesets_release.sh"
	chmod +x "$tools"/*.sh
}

run_cron() { # home
	local home=$1
	( env PATH="$BIN:$PATH" HOMEDIR="$home" FAKE_SLACK_LOG="$home/slack.log" FAKE_INTERNET="$home" \
		bash "$home/EnrichmentMap_monthlyGenesetBuild/GeneSetTools/create_genesets_cronjob.sh" > "$home/cron.out" 2>&1 )
}

start_test "monthly cron job reports a successful build (#43)"
h="$WORK/cron_ok"; setup_cron "$h" 0; run_cron "$h"
assert_contains "$h/slack.log" "Finished Geneset build - see log file for details"
assert_contains "$h/slack.log" "Content-type: application/json"
assert_not_contains "$h/slack.log" "FAILED"

start_test "monthly cron job reports a failed build (#43)"
h="$WORK/cron_fail"; setup_cron "$h" 1; run_cron "$h"
assert_contains "$h/slack.log" "Geneset build FAILED - see $(date '+%B_%d_%Y')_build.log"
assert_not_contains "$h/slack.log" "Finished Geneset build"
assert_contains "$h/slack.log" "Content-type: application/json"

#---------------------------------------------------------------------------
# create_genesets_stats_cronjob.sh (#43)
#---------------------------------------------------------------------------

run_stats_cron() { # dir summaries-exit-status
	local d=$1
	mkdir -p "$d"
	# ABSPATH is the server path - point the copy at the test folder instead
	sed "s#^ABSPATH=.*#ABSPATH=$d/#" "$TOOLS/geneset_tracking/create_genesets_stats_cronjob.sh" > "$d/create_genesets_stats_cronjob.sh"
	echo "https://hooks.slack.test/services/T000/B000/XXXX" > "$d/slack_webhook"
	printf '#!/bin/bash\nexit %s\n' "$2" > "$d/create_summaries.sh"
	chmod +x "$d"/*.sh
	( env PATH="$BIN:$PATH" FAKE_SLACK_LOG="$d/slack.log" FAKE_INTERNET="$d" bash "$d/create_genesets_stats_cronjob.sh" > "$d/cron.out" 2>&1 )
}

start_test "stats cron job reports success (#43)"
d="$WORK/stats_ok"; run_stats_cron "$d" 0
assert_contains "$d/slack.log" "Finished Geneset Stats build - see summary at http://download.baderlab.org/EM_Genesets/current_release/Human/README.html"
assert_contains "$d/slack.log" "Content-type: application/json"

start_test "stats cron job reports a failed report (#43)"
d="$WORK/stats_fail"; run_stats_cron "$d" 1
assert_contains "$d/slack.log" "Geneset Stats build FAILED for at least one species - see ${MONTH}_geneset_summary.log"
assert_not_contains "$d/slack.log" "Finished Geneset Stats build"

finish_tests
