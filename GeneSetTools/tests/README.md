# GeneSetTools tests

```
GeneSetTools/tests/run_tests.sh
```
runs everything (about 1 minute). Each suite can also be run on its own. Set
`KEEP_TEST_DIRS=1` to keep the temporary folders for inspection.

Nothing here touches the network, the real build server, `/mnt/build` or the
repo itself: every test works on copies in a temporary folder.

| Suite | Needs | What it checks |
|---|---|---|
| `shell/test_build_script.sh` | bash, python3, perl | `create_genesets_release.sh`. Single functions (downloads, file-name scraping, GMT post-processing, the publish check) are tested directly. Then the **whole build** is run three times in a sandbox: a normal run, a run where the human→mouse/rat/woodchuck conversions fail, and a run where downloads, BioPAX parsing and a translation fail. The outputs, file names, versions files, logs, Slack messages and the `current_release` link are checked after each. |
| `shell/test_cron_and_summaries.sh` | bash, python3 | `create_genesets_cronjob.sh` and `create_genesets_stats_cronjob.sh` post FAILED to Slack when the build/report fails; `create_summaries.sh` only replaces a species' README.html when its report rendered. |
| `java/run_java_tests.sh` | JDK 11+ | Compiles `src/` and runs `GenesetToolsTests.java`: ID batching, the job status loop, taxId filtering and error handling against a fake UniProt ID mapping server, and the `GenesetTools` command line (exit codes, no partial GMT on failure, timeouts) in a separate JVM. |
| `r/test_geneset_tracking.R` | R (rmarkdown, knitr, RCurl, ggplot2), python3, openssl | Renders `Geneset_tracking.Rmd` (without pandoc) against a fake download site whose newest release is a partial build, and checks every figure is made, not blank and included. Also checks: unreachable site, 404 handling, and SSL verification (a self-signed server is rejected). |

## How the build script is run without the real tools

`shell/fakes/make_sandbox.py` creates a TOOLDIR with a copy of the real
`create_genesets_release.sh` and puts stand-ins first on the `PATH`:

- `fake_curl.py` serves files from a fake "internet" folder. A missing URL
  behaves like an HTTP 404 (`--fail` → exit 22, otherwise the error page is
  saved), and every call and Slack post is logged.
- `fake_genesettools.py` stands in for `java -jar GenesetTools.jar`. It writes
  files with the same **names** the real tool uses (the script finds files by
  name) and logs every call. Failures can be injected with `FAKE_FAIL_TOGSEA`,
  `FAKE_FAIL_TRANSLATE` and `FAKE_FAIL_CONVERT`.
- The BioPAX validator and DrugBank parser are replaced with small scripts, and
  the static source files are tiny fake versions (the version notes are copied
  from the repo).
- The build runs on Linux with GNU tools. On a Mac, small shims are added for
  `ls --ignore`, `grep -P`, `tar --wildcards` and GNU-style `wc` output, and
  only when the local tools don't support them.

`PUBLISH_DIR` points the publish step at the sandbox instead of `/mnt/build/EM_Genesets`.

## Checking that the tests catch the bugs

Each test is labelled with the GitHub issue it covers. The tests were checked
against the code before the fixes (the build-script and cron tests fail there),
and by putting each fixed bug back one at a time: every reintroduced bug made at
least one test fail.
