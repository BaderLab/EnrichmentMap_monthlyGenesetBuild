# Tests for geneset_tracking/Geneset_tracking.Rmd (GitHub issues #44-#51).
#
#   Rscript tests/r/test_geneset_tracking.R
#
# Needs R with rmarkdown, knitr, RCurl and ggplot2, plus python3 (for a local
# web server) and openssl (for a self-signed certificate). pandoc is NOT needed:
# the report is knitted with run_pandoc = FALSE.
#
# A fake download.baderlab.org is built from small files in the same formats as
# the real ones (Summary_GeneSet_Counts_*.txt, Build_summary_stats_*.log and the
# *_translation_summary.log files) and served on 127.0.0.1. Its newest release
# is a partial build (no summary files, a listed log file that doesn't exist),
# which is what used to crash the report.

`%||%` <- function(a, b) if (is.null(a)) b else a

# on.exit() does nothing at the top level of an Rscript, so clean up explicitly
cleanup_steps <- list()
add_cleanup <- function(f) cleanup_steps[[length(cleanup_steps) + 1]] <<- f
finish <- function(status) {
  for (f in rev(cleanup_steps)) try(f(), silent = TRUE)
  quit(status = status)
}

checks <- 0
failures <- 0
current_test <- ""

start_test <- function(name) {
  current_test <<- name
  cat("---", name, "\n")
}
check <- function(ok, message) {
  checks <<- checks + 1
  if (!isTRUE(ok)) {
    failures <<- failures + 1
    cat("  FAIL [", current_test, "]: ", message, "\n", sep = "")
  }
}

script_arg <- grep("^--file=", commandArgs(FALSE), value = TRUE)
test_dir <- if (length(script_arg)) dirname(normalizePath(sub("^--file=", "", script_arg))) else getwd()
rmd <- normalizePath(file.path(test_dir, "..", "..", "geneset_tracking", "Geneset_tracking.Rmd"))

for (p in c("rmarkdown", "knitr", "RCurl", "ggplot2")) {
  if (!requireNamespace(p, quietly = TRUE)) stop("R package ", p, " is needed for these tests")
}

# not under R's own tempdir(), which R deletes when it exits (KEEP_TEST_DIRS)
work <- tempfile("geneset_tracking_tests_", tmpdir = Sys.getenv("TMPDIR", "/tmp"))
dir.create(work)
if (Sys.getenv("KEEP_TEST_DIRS") == "") add_cleanup(function() unlink(work, recursive = TRUE)) else cat("test files kept in", work, "\n")

# don't wait for retries in the tests
options(geneset_tracking.download_tries = 2, geneset_tracking.max_wait = 0)

#--------------------------------------------------------------------------
# fake download site
#--------------------------------------------------------------------------
site <- file.path(work, "site")
write_file <- function(path, lines) {
  dir.create(dirname(path), recursive = TRUE, showWarnings = FALSE)
  writeLines(lines, path)
}

translation_record <- function(gmt, total, untranslated) {
  c(paste0("File name:\t", gmt),
    "original Identifier\tentrezgene",
    "ID translated to\tsymbol",
    paste0("Total number of genes in the file:\t", total),
    paste0("total Number of genes in file successfully converted:\t", total - 2),
    "total Number Identifiers unable to map\t2",
    paste0("Percentage ids not translated\t", round(200 / total, 3), "%"),
    paste0("Total annotations in the file\t", total * 3),
    paste0("Total Untranslated annotations\t", untranslated),
    paste0("Percentage annotations not translated\t", round(100 * untranslated / (total * 3), 3), "%"),
    "===================================")
}

make_release <- function(name, scale) {
  human <- file.path(site, name, "Human")
  date <- sub("/$", "", name)
  n <- function(x) round(x * scale)
  write_file(file.path(human, "Summary_GeneSet_Counts_entrezgene.txt"), c(
    paste("Gene Set Statistics for Release ", date),
    "GO with IEA Stats:",
    paste(n(13657), "Human_GO_bp_with_GO_iea_entrezgene.gmt"),
    paste(n(6485), "Human_GO_mf_with_GO_iea_entrezgene.gmt"),
    "Total GO with IEA:",
    paste0(n(22234), " Human_GOALL_with_GO_iea_", date, "_entrezgene.gmt"),
    "------------------",
    "Pathway Stats:",
    paste0("     ", n(223), " Human_NCI_Nature_", date, "_Entrezgene.gmt"),
    paste0("    ", n(2834), " Human_Reactome_", date, "_Entrezgene.gmt"),
    paste0("  ", n(55904), " Human_PFOCR_", date, "_Entrezgene.gmt"),
    paste0("   ", n(991), " Human_WikiPathways_", date, "_Entrezgene.gmt"),
    paste0("  ", n(60000), " total"),
    "------------------",
    "miR Stats:",
    paste(n(2598), "Human_miRs_MSigdb_entrezgene.gmt"),
    "Disease Phenotypes Stats:",
    paste(n(11935), "Human_DiseasePhenotypes_entrezgene.gmt"),
    "Drug Targets Stats:",
    paste(n(1500), "Human_DrugBank_all_entrezgene.gmt")))
  # same layout as GetAllStats.sh output: the first data row has no leading tab
  row <- function(f, total, untranslated, lead = "\t")
    paste0(lead, paste(f, "entrezgene", "symbol", total, total - 5, 5, "1%", total * 3, untranslated, "2%", sep = "\t"), "\t")
  write_file(file.path(human, "Build_summary_stats_symbol.log"), c(
    paste("Filename", "Original Identifier", "ID translated", "Total #genes", " #genes translated", "#genes missing",
          " percent not translated", " Total Annotations", " Annotations not translated ", " percent annot not translated", sep = "\t"),
    "",
    row("WikiPathways_Homo_sapiens_entrezgene.gmt", n(9031), n(655), lead = ""),
    row("Human_GO_bp_with_GO_iea_UniProt.gmt", n(18000), n(900)),
    row("Human_GO_bp_no_GO_iea_UniProt.gmt", n(17000), n(800)),
    row("Human_GO_mf_with_GO_iea_UniProt.gmt", n(16000), n(700)),
    row("Human_GO_cc_with_GO_iea_UniProt.gmt", n(15000), n(600)),
    row("Homosapiens.owl_UniProt.gmt", n(11653), n(4012))))
  sym <- file.path(human, "symbol")
  write_file(file.path(sym, "Pathways", "Human_Reactome_symbol_translation_summary.log"),
             translation_record("Homosapiens.owl_UniProt.gmt", n(11653), n(4012)))
  write_file(file.path(sym, "Pathways", "Human_WikiPathways_symbol_translation_summary.log"),
             translation_record("WikiPathways_Homo_sapiens_entrezgene.gmt", n(9031), n(655)))
  # two records in one log (the >12 line case)
  write_file(file.path(sym, "Pathways", "Human_IOB_symbol_translation_summary.log"),
             c(translation_record("Wnt.owl_Entrezgene.gmt", n(66), 0), translation_record("Notch.owl_Entrezgene.gmt", n(128), 3)))
  write_file(file.path(sym, "DrugTargets", "Human_DrugBank_symbol_translation_summary.log"),
             translation_record("Human_DrugBank_all_UniProt.gmt", n(3000), n(120)))
  write_file(file.path(sym, "miRs", "Human_miRs_MSigdb_symbol_translation_summary.log"),
             translation_record("c3.mir.v2026.1.entrezgene.gmt", n(4000), n(80)))
  write_file(file.path(sym, "DiseasePhenotypes", "Human_DiseasePhenotypes_symbol_translation_summary.log"),
             translation_record("Human_DiseasePhenotypes_entrezgene.gmt", n(5000), n(50)))
}

releases <- c("November_01_2026/", "October_01_2026/", "September_14_2026/", "March_02_2026/", "January_01_2019/")
make_release("October_01_2026/", 1.00)
make_release("September_14_2026/", 0.98)
make_release("March_02_2026/", 0.95)
# newest release = a partial build: no summary files, and a listed log that isn't there (404)
write_file(file.path(site, "November_01_2026", "Human", "November_01_2026_versions.txt"), "versions only")
write_file(file.path(site, "November_01_2026", "Human", "symbol", "Pathways", "index.html"),
           '<html><body><a href="Human_Reactome_symbol_translation_summary.log">Human_Reactome_symbol_translation_summary.log</a></body></html>')
dir.create(file.path(site, "January_01_2019", "Human"), recursive = TRUE)
# release list, newest first like the real server
write_file(file.path(site, "index.html"), c("<html><body><pre>",
  '<a href="current_release/">current_release/</a>',
  sprintf('<a href="%s">%s</a>            2026-11-03 00:21    -', releases, releases),
  '<a href="index.php">index.php</a>', "</pre></body></html>"))

free_port <- function() {
  as.integer(system2("python3", c("-c", shQuote("import socket; s=socket.socket(); s.bind(('127.0.0.1',0)); print(s.getsockname()[1])")), stdout = TRUE))
}
start_server <- function(args, port, url) {
  pid_file <- tempfile(tmpdir = work)
  system2("bash", c("-c", shQuote(paste0("python3 ", args, " > /dev/null 2>&1 & echo $! > ", pid_file))), wait = TRUE)
  for (i in 1:50) {
    ok <- tryCatch({ suppressWarnings(readLines(url(url), warn = FALSE)); TRUE }, error = function(e) FALSE)
    if (ok) break
    Sys.sleep(0.2)
  }
  readLines(pid_file)
}

port <- free_port()
site_url <- sprintf("http://127.0.0.1:%d/", port)
http_pid <- start_server(sprintf("-m http.server %d --bind 127.0.0.1 -d %s", port, shQuote(site)), port, site_url)
add_cleanup(function() system2("kill", http_pid))

#--------------------------------------------------------------------------
# helpers
#--------------------------------------------------------------------------
render_report <- function(name, download_url) {
  home <- file.path(work, name)
  dir.create(file.path(home, "figures"), recursive = TRUE)
  file.copy(rmd, home)
  input <- file.path(home, "Geneset_tracking.Rmd")
  err <- NULL
  out <- tryCatch(
    rmarkdown::render(input, params = list(species = "Human", home_dir = home, download_url = download_url),
                      run_pandoc = FALSE, quiet = TRUE, envir = new.env(), intermediates_dir = home,
                      knit_root_dir = home),
    error = function(e) { err <<- conditionMessage(e); NULL })
  md <- list.files(home, pattern = "\\.knit\\.md$|\\.md$", full.names = TRUE)
  list(home = home, error = err, md = if (length(md)) paste(readLines(md[1], warn = FALSE), collapse = "\n") else "")
}

# load the helper functions defined in the Rmd
helpers <- new.env()
code <- knitr::purl(rmd, output = file.path(work, "report.R"), quiet = TRUE, documentation = 0)
for (e in parse(code)) {
  if (is.call(e) && identical(e[[1]], as.name("<-")) && is.name(e[[2]]) &&
      as.character(e[[2]]) %in% c("safe_getURL", "safe_download.file")) {
    eval(e, helpers)
  }
}

# size of a blank png of the size the report uses, to tell drawn figures from blank ones
blank_png <- file.path(work, "blank.png")
png(blank_png, width = 600, height = 600, pointsize = 14); plot.new(); dev.off()
blank_size <- file.size(blank_png)

#--------------------------------------------------------------------------
# tests
#--------------------------------------------------------------------------
start_test("report renders when the newest release is a partial build (#44, #45, #47)")
r <- render_report("full", site_url)
check(is.null(r$error), paste("render failed:", r$error))

fig_dir <- list.files(file.path(r$home, "figures"), full.names = TRUE)
figures <- list.files(fig_dir, pattern = "\\.png$", full.names = TRUE)
fig <- function(name) { f <- figures[basename(figures) == name]; if (length(f)) f[1] else NA }

start_test("all figures are made, including Drug, miR and Disease (#48)")
for (f in c("Overall_geneset_stats.png", "GO_geneset_stats.png", "Pathway_geneset_stats.png",
            "Pathway_identifier_stats.png", "Drugbank_identifier_stats.png", "Mir_identifier_stats.png",
            "Disease_identifier_stats.png", "GO_identifier_stats.png")) {
  check(!is.na(fig(f)), paste("figure not made:", f))
}

start_test("figures made inside if blocks are not blank (#49)")
for (f in c("Drugbank_identifier_stats.png", "Mir_identifier_stats.png", "Disease_identifier_stats.png",
            "Overall_geneset_stats.png")) {
  if (!is.na(fig(f))) {
    check(file.size(fig(f)) > 3 * blank_size,
          sprintf("%s looks blank (%d bytes; a blank png is %d bytes)", f, file.size(fig(f)), blank_size))
  }
}

start_test("Drug, miR and Disease figures are included in the report (#50)")
for (f in c("Drugbank_identifier_stats.png", "Mir_identifier_stats.png", "Disease_identifier_stats.png",
            "Overall_geneset_stats.png")) {
  check(grepl(f, r$md, fixed = TRUE), paste(f, "is not in the rendered report"))
}

start_test("the partial newest release is skipped, older releases are used (#45, #47)")
check(grepl("Pathway_identifier_stats.png", r$md, fixed = TRUE), "pathway identifier figure in report")
check(!file.exists(file.path(r$home, "Human_Reactome_symbol_translation_summary.log")) ||
        file.size(file.path(r$home, "Human_Reactome_symbol_translation_summary.log")) > 0,
      "no empty log file left from the failed download")

start_test("unreachable download site stops with a clear message (#46)")
closed <- free_port()
r2 <- render_report("unreachable", sprintf("http://127.0.0.1:%d/", closed))
check(!is.null(r2$error), "render should fail")
check(grepl("Could not read the list of releases from", r2$error %||% ""),
      paste("error message was:", r2$error))

start_test("safe_download.file returns FALSE for a missing file without waiting (#45)")
t0 <- Sys.time()
dest <- file.path(work, "dl", "missing.log")
res <- with(helpers, safe_download.file(paste0(site_url, "October_01_2026/Human/no_such_file.log"), dest, tries = 15, max_wait = 60))
check(identical(res, FALSE), "returns FALSE")
check(!file.exists(dest), "no file left behind")
check(as.numeric(difftime(Sys.time(), t0, units = "secs")) < 10, "a 404 is not retried")
res <- with(helpers, safe_download.file(paste0(site_url, "October_01_2026/Human/Summary_GeneSet_Counts_entrezgene.txt"), dest))
check(identical(res, TRUE) && file.size(dest) > 0, "returns TRUE and saves the file when it exists")

start_test("SSL certificates are verified (#51)")
rmd_text <- paste(readLines(rmd), collapse = "\n")
check(!grepl("ssl.verifypeer *= *FALSE", rmd_text), "ssl.verifypeer = FALSE is still in the Rmd")
check(!grepl("ssl.verifyhost *= *FALSE", rmd_text), "ssl.verifyhost = FALSE is still in the Rmd")
openssl <- Sys.which("openssl")
if (nzchar(openssl)) {
  key <- file.path(work, "key.pem"); cert <- file.path(work, "cert.pem")
  system2(openssl, c("req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", key, "-out", cert,
                     "-days", "1", "-subj", "/CN=127.0.0.1"), stdout = FALSE, stderr = FALSE)
  https_port <- free_port()
  server_py <- file.path(work, "https_server.py")
  writeLines(c("import http.server, ssl, sys",
               "httpd = http.server.HTTPServer(('127.0.0.1', int(sys.argv[1])), http.server.SimpleHTTPRequestHandler)",
               "ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); ctx.load_cert_chain(sys.argv[2], sys.argv[3])",
               "httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)",
               "httpd.serve_forever()"), server_py)
  https_pid <- start_server(paste(shQuote(server_py), https_port, shQuote(cert), shQuote(key)), https_port,
                            sprintf("http://127.0.0.1:%d/", port))
  Sys.sleep(1)
  got <- suppressMessages(with(helpers, safe_getURL(sprintf("https://127.0.0.1:%d/", https_port))))
  check(is.na(got), "a server with a self-signed certificate must be rejected")
  system2("kill", https_pid)
} else {
  cat("  (openssl not found - skipped the self-signed certificate check)\n")
}
got <- with(helpers, safe_getURL(site_url))
check(!is.na(got) && grepl("October_01_2026", got), "plain http still works")

cat("\n")
if (failures == 0) {
  cat("PASSED:", checks, "checks\n")
  finish(0)
}
cat("FAILED:", failures, "of", checks, "checks failed\n")
finish(1)
