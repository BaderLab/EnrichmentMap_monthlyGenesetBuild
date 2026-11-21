"""Download one file (or the newest file on an index page matching --pattern) with retries + SHA-256.

Writes the file to --out and a provenance record to --out.json (url, time, Last-Modified, size, sha256).
"""
import argparse
import re

import requests

from common import USER_AGENT, download


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url")
    ap.add_argument("--index", help="index page to scrape for a file name")
    ap.add_argument("--pattern", help="regex for the file name on --index")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    url = a.url
    if a.index:
        html = requests.get(a.index, timeout=60, headers={"User-Agent": USER_AGENT}).text
        names = sorted(set(re.findall(a.pattern, html)))
        if not names:
            raise SystemExit(f"no file matching {a.pattern!r} on {a.index} - the page layout changed?")
        url = a.index.rstrip("/") + "/" + names[-1]
    download(url, a.out)


if __name__ == "__main__":
    main()
