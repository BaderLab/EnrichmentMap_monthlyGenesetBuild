#!/usr/bin/env python3
"""Stand-in for curl used by the shell tests.

URLs are served from the directory in $FAKE_INTERNET:
  https://host/a/b.txt  ->  $FAKE_INTERNET/host/a/b.txt
  https://host/dir/     ->  $FAKE_INTERNET/host/dir/index.html
A URL with no file behind it behaves like an HTTP 404: with -f/--fail curl
exits 22 and writes nothing; without it the 404 error page is saved as if it
were the data (which is what the real curl does).

Every call is logged (one tab-separated line) to $FAKE_CURL_LOG:
  method  flags  url  output-file  user
POST bodies (Slack messages) are appended to $FAKE_SLACK_LOG with the
Content-type header.
"""
import os
import sys

VALUE_SHORT = set("oudwXHT")
VALUE_LONG = {"--output", "--data", "--user", "--write-out", "--request", "--header"}

args = sys.argv[1:]
flags = set()
opts = {}
headers = []
urls = []
i = 0
while i < len(args):
    a = args[i]
    if a.startswith("--"):
        if a in VALUE_LONG:
            val = args[i + 1]
            i += 1
            key = {"--output": "o", "--data": "d", "--user": "u", "--write-out": "w",
                   "--request": "X", "--header": "H"}[a]
            if key == "H":
                headers.append(val)
            else:
                opts[key] = val
        elif a == "--fail":
            flags.add("f")
        elif a == "--silent":
            flags.add("s")
        elif a == "--location":
            flags.add("L")
        elif a == "--head":
            flags.add("I")
        else:
            flags.add(a)
    elif a.startswith("-") and len(a) > 1:
        letters = a[1:]
        for j, c in enumerate(letters):
            if c in VALUE_SHORT:
                rest = letters[j + 1:]
                if rest:
                    val = rest
                else:
                    val = args[i + 1]
                    i += 1
                if c == "H":
                    headers.append(val)
                else:
                    opts[c] = val
                break
            flags.add(c)
    else:
        urls.append(a)
    i += 1

method = opts.get("X", "GET")
url = urls[0] if urls else ""

log = os.environ.get("FAKE_CURL_LOG")
if log:
    with open(log, "a") as fh:
        fh.write("\t".join([method, "".join(sorted(f for f in flags if len(f) == 1)) + " " + " ".join(sorted(f for f in flags if len(f) > 1)),
                            url, opts.get("o", "-"), opts.get("u", "-")]) + "\n")

if method == "POST":
    slack = os.environ.get("FAKE_SLACK_LOG")
    if slack:
        with open(slack, "a") as fh:
            fh.write("HEADERS=" + "|".join(headers) + "\tDATA=" + opts.get("d", "") + "\n")
    sys.exit(0)

internet = os.environ["FAKE_INTERNET"]
path = url.split("://", 1)[-1]
local = os.path.join(internet, path)
if path.endswith("/") or os.path.isdir(local):
    local = os.path.join(local, "index.html")
# collapse the '//' that the build script produces with ${URL}/${FILE}
local = os.path.normpath(local)
found = os.path.isfile(local)

if "I" in flags:
    if found:
        sys.stdout.write("HTTP/1.1 200 OK\r\nLast-Modified: Wed, 01 Oct 2026 10:00:00 GMT\r\n\r\n")
        sys.exit(0)
    sys.stdout.write("HTTP/1.1 404 Not Found\r\n\r\n")
    sys.exit(22 if "f" in flags else 0)

if found:
    with open(local, "rb") as fh:
        data = fh.read()
    status = 0
else:
    if "f" in flags:
        if "S" in flags or "s" not in flags:
            sys.stderr.write("curl: (22) The requested URL returned error: 404\n")
        sys.exit(22)
    data = b"<html><head><title>404 Not Found</title></head><body><h1>Not Found</h1></body></html>\n"
    status = 0

if "o" in opts:
    with open(opts["o"], "wb") as fh:
        fh.write(data)
else:
    sys.stdout.buffer.write(data)
if "w" in opts:
    sys.stdout.write("200" if found else "404")
sys.exit(status)
