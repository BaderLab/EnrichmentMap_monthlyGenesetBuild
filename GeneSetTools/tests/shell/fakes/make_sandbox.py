#!/usr/bin/env python3
"""Build a sandbox in which create_genesets_release.sh can run end to end
without the network, the real GenesetTools.jar, BioPAX validator or licensed data.

usage: make_sandbox.py <sandbox_dir> <repo GeneSetTools dir>

Creates:
  <sandbox>/tool        the TOOLDIR: the real build script + GetAllStats.sh,
                        fake static source files, fake validator / perl parser
  <sandbox>/internet    files served by fake_curl.py (host/path layout)
  <sandbox>/publish     PUBLISH_DIR, with an existing 'current_release' link
                        pointing at an older release
  <sandbox>/bin         curl/java/wget stand-ins (+ GNU shims where needed)
"""
import gzip
import io
import os
import shutil
import stat
import subprocess
import sys
import tarfile
import zipfile

sandbox, repo = sys.argv[1], sys.argv[2]
fakes = os.path.dirname(os.path.abspath(__file__))
tool = os.path.join(sandbox, "tool")
net = os.path.join(sandbox, "internet")
bindir = os.path.join(sandbox, "bin")
publish = os.path.join(sandbox, "publish")


def write(path, text, mode="w"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, mode) as fh:
        fh.write(text)


def executable(path, text):
    write(path, text)
    os.chmod(path, os.stat(path).st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def owl(name, taxid="9606"):
    return ('<?xml version="1.0"?>\n<rdf:RDF><bp:Pathway rdf:ID="%s"><bp:organism>%s</bp:organism>'
            '</bp:Pathway></rdf:RDF>\n' % (name, taxid))


def gmt_line(name, source, genes):
    return "%s%%%s%%%s\t%s\t%s\n" % (name, source, name, name, "\t".join(genes))


def zip_bytes(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for n, t in files.items():
            z.writestr(n, t)
    return buf.getvalue()


def tgz_bytes(files):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz") as t:
        for n, text in files.items():
            data = text.encode()
            info = tarfile.TarInfo(n)
            info.size = len(data)
            t.addfile(info, io.BytesIO(data))
    return buf.getvalue()


for d in (tool, net, bindir, publish):
    os.makedirs(d, exist_ok=True)

# ---- the code under test (copied, so the sandbox can't touch the repo) ----
shutil.copy(os.path.join(repo, "create_genesets_release.sh"), tool)
shutil.copy(os.path.join(repo, "GetAllStats.sh"), tool)
write(os.path.join(tool, "GenesetTools.jar"), "fake jar - see tests/shell/fakes/fake_genesettools.py\n")
write(os.path.join(tool, "slack_webhook"), "https://hooks.slack.test/services/T000/B000/XXXX\n")

# ---- tool helpers that are external files on the real build server ----
executable(os.path.join(tool, "biopax-validator-5.0.0-SNAPSHOT", "validate.sh"),
           '#!/bin/bash\n# fake validator: "auto-fix" = copy to <name>.modified.owl in the current dir\n'
           'f=${1#file:}\ncp "$f" "$(basename "$f").modified.owl"\n')
write(os.path.join(tool, "scripts", "parseDrugBankXml.pl"),
      '#!/usr/bin/perl\n# fake DrugBank parser: write one gene set to the -o file\n'
      'my %a = @ARGV; open(my $fh, ">", $a{"-o"}) or die; '
      'print $fh "DB00001 drug%DrugBank%DB00001\\tDB00001 drug\\tP04637\\tP38398\\n"; close $fh;\n')
write(os.path.join(tool, "testFiles", "homologene.data"), "")
write(os.path.join(tool, "woodchuck_gmt_conversion", "homologroupHumanWoodchuck_May29_2021_addedcol.tsv"), "")

# ---- static source files ----
static = os.path.join(tool, "staticSrcFiles")
repo_static = os.path.join(repo, "staticSrcFiles")
write(os.path.join(static, "biocyc", "human.tar.gz"),
      tgz_bytes({"humancyc/29.0/x.txt": "not owl\n", "humancyc/biopax-level3.owl": owl("HumanCyc")}), "wb")
shutil.copy(os.path.join(repo_static, "biocyc", "humancyc.txt"), os.path.join(static, "biocyc"))
write(os.path.join(static, "NCI", "NCI-Nature_Curated.bp3.owl.gz"), gzip.compress(owl("NCI").encode()), "wb")
shutil.copy(os.path.join(repo_static, "NCI", "NCI_Nature.txt"), os.path.join(static, "NCI"))
for n in (1, 2):
    write(os.path.join(static, "NetPath", "NetPath_modified", "NetPath_%d.owl" % n), owl("NetPath_%d" % n))
# the real version note committed in the repo
shutil.copy(os.path.join(repo_static, "NetPath", "NetPath.txt"), os.path.join(static, "NetPath"))
write(os.path.join(static, "Drugbank", "drugbank.xml.zip"), zip_bytes({"full database.xml": "<drugbank/>\n"}), "wb")
write(os.path.join(static, "Drugbank", "DrugBank.txt"), "DrugBank\nstatic copy\n=====\n")
write(os.path.join(static, "IOB", "Wnt.owl_updated_v1.owl"), owl("Wnt"))
shutil.copy(os.path.join(repo_static, "IOB", "IOB.txt"), os.path.join(static, "IOB"))
write(os.path.join(static, "KEGG", "July2011_Human_KEGG_symbol.gmt"), gmt_line("KEGG pathway", "KEGG", ["TP53", "BRCA1"]))
shutil.copy(os.path.join(repo_static, "KEGG", "KEGG.txt"), os.path.join(static, "KEGG"))
# (file names follow the earlier releases, e.g. c2.cp.cpfiltered.v2023.1.entrezgene.gmt)
for d, txt, name in (("msigdb_path", "msigdb_path.txt", "c2.cp.cpfiltered.v2026.1.entrezgene.gmt"),
                     ("msigdb_hall", "msigdb_hall.txt", "h.all.v2026.1.entrezgene.gmt"),
                     ("mirs", "msigdb_mirs.txt", "c3.mir.v2026.1.entrezgene.gmt"),
                     ("tf", "msigdb_tf.txt", "c3.tft.v2026.1.entrezgene.gmt")):
    write(os.path.join(static, d, name), "%s_SET\thttp://msigdb\t7157\t672\n" % d.upper())
    shutil.copy(os.path.join(repo_static, d, txt), os.path.join(static, d))

# ---- the fake internet ----
def serve(url_path, data):
    write(os.path.join(net, url_path), data, "wb" if isinstance(data, bytes) else "w")


serve("data.pantherdb.org/ftp/pathway/current_release/BioPAX.tar.gz",
      tgz_bytes({"BioPAX/P00001.owl": owl("P00001"), "BioPAX/P00002.owl": owl("P00002")}))
serve("pathbank.org/downloads/pathbank_primary_biopax.zip",
      zip_bytes({"PW000001.owl": owl("PW000001"), "PW000002.owl": owl("PW000002", "10090")}))
serve("reactome.org/ContentService/data/database/version", "95")
serve("download.reactome.org/95/biopax.zip",
      zip_bytes({"Homo_sapiens.owl": owl("Reactome_human"), "Rattus_norvegicus.owl": owl("Reactome_rat", "10116"),
                 "Mus_musculus.owl": owl("Reactome_mouse", "10090")}))

def listing_row(fn):
    # same markup as data.wikipathways.org's file listing (Nov 2026) - the build
    # script scrapes file names out of it with grep, so the format matters
    return ('<tr><td><a class=file-link id=File href="%s">%s</a></td><td>48K</td>'
            '<td>10 Oct 2026  3:29 PM</td></tr>' % (fn, fn))


wp_species = {"Homo_sapiens": ["7157", "672"], "Mus_musculus": ["22059", "12189"], "Rattus_norvegicus": ["24842", "497672"]}
links = []
for sp, genes in wp_species.items():
    fn = "wikipathways-20261010-gmt-%s.gmt" % sp
    links.append(listing_row(fn))
    serve("data.wikipathways.org/current/gmt/" + fn,
          "WP_%s pathway%%WikiPathways_20261010%%WP1%%%s\thttps://www.wikipathways.org/WP1\t%s\n" % (sp, sp, "\t".join(genes)))
serve("data.wikipathways.org/current/gmt/index.html", "<html><body>\n%s\n</body></html>\n" % "\n".join(links))

links = []
for sp, genes in (("Homo_sapiens", ["7157", "672"]), ("Mus_musculus", ["22059"])):
    for kind in ("gmt", "chemical-gmt"):
        fn = "pfocr-20250701-%s-%s.gmt" % (kind, sp)
        links.append(listing_row(fn))
        serve("data.wikipathways.org/pfocr/current/" + fn,
              "PFOCR %s %s figure%%PFOCR_20250701%%PMC1%%%s\tfigure\t%s\n" % (sp, kind, sp, "\t".join(genes)))
serve("data.wikipathways.org/pfocr/current/index.html", "<html><body>\n%s\n</body></html>\n" % "\n".join(links))

for sp, fn in (("HUMAN", "goa_human.gaf.gz"), ("MOUSE", "goa_mouse.gaf.gz"), ("RAT", "goa_rat.gaf.gz")):
    serve("ftp.ebi.ac.uk/pub/databases/GO/goa/%s/%s" % (sp, fn), gzip.compress(b"!gaf-version: 2.2\nUniProtKB\tP04637\tTP53\n"))
serve("current.geneontology.org/ontology/go.obo", "format-version: 1.2\n")
serve("github.com/obophenotype/human-phenotype-ontology/releases/latest/download/genes_to_phenotype.txt", "ncbi_gene_id\tgene_symbol\n7157\tTP53\n")
serve("raw.githubusercontent.com/obophenotype/human-phenotype-ontology/master/hp.obo", "format-version: 1.2\n")

# ---- an older release that current_release points at before the build ----
os.makedirs(os.path.join(publish, "October_01_2026", "Human"), exist_ok=True)
os.symlink("October_01_2026/", os.path.join(publish, "current_release"))

# ---- stand-in commands ----
executable(os.path.join(bindir, "curl"), '#!/bin/bash\nexec python3 "%s" "$@"\n' % os.path.join(fakes, "fake_curl.py"))
executable(os.path.join(bindir, "java"), '#!/bin/bash\nexec python3 "%s" "$@"\n' % os.path.join(fakes, "fake_genesettools.py"))
executable(os.path.join(bindir, "wget"), '#!/bin/bash\necho "fake wget: no network in tests" >&2\nexit 1\n')

# The build runs on Linux with GNU tools. Only where this machine's tools lack
# the GNU options the script uses (e.g. macOS), add small shims for them.
def supports(cmd):
    return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, cwd=sandbox).returncode == 0

if not supports(["ls", "--ignore=x", "."]):
    real_ls = shutil.which("ls")
    executable(os.path.join(bindir, "ls"), '''#!/bin/bash
# GNU 'ls --ignore=PATTERN' shim for systems whose ls doesn't have it
ignore=(); args=()
for a in "$@"; do
	case "$a" in
		--ignore=*) ignore+=("${a#--ignore=}") ;;
		*) args+=("$a") ;;
	esac
done
if [ ${#ignore[@]} -eq 0 ]; then exec %s "$@"; fi
%s ${args[@]+"${args[@]}"} | while IFS= read -r f; do
	skip=0
	for p in "${ignore[@]}"; do case "$f" in $p) skip=1 ;; esac; done
	[ $skip = 0 ] && echo "$f"
done
exit 0
''' % (real_ls, real_ls))
if subprocess.run(["grep", "-o", "-P", "(?<=a)b"], input=b"ab\n", stdout=subprocess.DEVNULL,
                  stderr=subprocess.DEVNULL).returncode != 0:
    real_grep = shutil.which("grep")
    executable(os.path.join(bindir, "grep"), '''#!/bin/bash
# shim: GNU 'grep -P' (perl regexes) for greps without it (macOS). Only -o and -v are
# combined with -P in the build script; everything else goes to the real grep.
for a in "$@"; do
	case "$a" in
		-P|-[a-zA-Z]*P*) exec python3 -c '
import re, sys
args = sys.argv[1:]
flags = ""
rest = []
for a in args:
    if a.startswith("-") and len(a) > 1 and not rest:
        flags += a[1:]
    else:
        rest.append(a)
pattern, files = rest[0], rest[1:]
rx = re.compile(pattern)
found = False
streams = [open(f) for f in files] or [sys.stdin]
for fh in streams:
    for line in fh:
        line = line.rstrip("\\n")
        if "o" in flags:
            for m in rx.finditer(line):
                if m.group(0):
                    print(m.group(0)); found = True
        elif bool(rx.search(line)) != ("v" in flags):
            print(line); found = True
sys.exit(0 if found else 1)
' "$@" ;;
	esac
done
exec %s "$@"
''' % real_grep)

# GNU wc prints "1", BSD wc prints "       1"; the script compares the output as a string
# ([[ `grep 9606 $file | wc -l` > 0 ]]), so pad-free output matters
if subprocess.run(["wc", "-l"], input=b"x\n", stdout=subprocess.PIPE).stdout.startswith(b" "):
    real_wc = shutil.which("wc")
    executable(os.path.join(bindir, "wc"), '''#!/bin/bash
# shim: GNU-style wc output (no leading padding)
%s "$@" | sed -e 's/^ *//' -e 's/  */ /'
exit ${PIPESTATUS[0]}
''' % real_wc)

tar_probe = os.path.join(sandbox, "probe.tar.gz")
with open(tar_probe, "wb") as fh:
    fh.write(tgz_bytes({"probe/a.owl": "x"}))
if not supports(["tar", "--wildcards", "-tzf", tar_probe, "probe/*.owl"]):
    real_tar = shutil.which("tar")
    executable(os.path.join(bindir, "tar"), '''#!/bin/bash
# shim: drop GNU --wildcards for tars that always match wildcards (bsdtar)
args=()
for a in "$@"; do [ "$a" = "--wildcards" ] || args+=("$a"); done
exec %s "${args[@]}"
''' % real_tar)
os.remove(tar_probe)
