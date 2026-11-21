"""Small helpers shared by the pipeline scripts: downloads, checksums, GMT reading."""
import gzip
import hashlib
import json
import time
from datetime import datetime, timezone
from pathlib import Path

import requests

USER_AGENT = "geneset-build-pipeline/0.1 (python-requests)"


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url, dest, retries=3, timeout=300, min_bytes=1):
    """Download url to dest, retrying with exponential backoff (5 s, 10 s, 20 s ...).

    Raises after the last failed try, so Snakemake marks the job (and only that job) failed.
    Writes dest + '.json' with url, time, size and SHA-256.
    """
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    last_err = None
    for attempt in range(retries):
        try:
            with requests.get(url, stream=True, timeout=timeout,
                              headers={"User-Agent": USER_AGENT}) as r:
                r.raise_for_status()
                with open(tmp, "wb") as fh:
                    for chunk in r.iter_content(1 << 20):
                        fh.write(chunk)
                last_modified = r.headers.get("Last-Modified")
            if tmp.stat().st_size < min_bytes:
                raise IOError(f"{url}: only {tmp.stat().st_size} bytes")
            tmp.rename(dest)
            meta = {
                "url": url,
                "downloaded_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "last_modified": last_modified,
                "bytes": dest.stat().st_size,
                "sha256": sha256(dest),
            }
            Path(str(dest) + ".json").write_text(json.dumps(meta, indent=2))
            return meta
        except Exception as e:  # noqa: BLE001 - we retry on anything
            last_err = e
            if attempt < retries - 1:
                time.sleep(5 * 2 ** attempt)
    raise RuntimeError(f"download failed after {retries} tries: {url}: {last_err}")


def open_text(path):
    """Open text or gzip (detected from the file's first bytes, not its name)."""
    path = str(path)
    with open(path, "rb") as fh:
        is_gz = fh.read(2) == b"\x1f\x8b"
    if is_gz:
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return open(path, encoding="utf-8", errors="replace")


def read_gmt(path):
    """Yield (name, description, genes) for each non-empty GMT line."""
    with open_text(path) as fh:
        for line in fh:
            parts = line.rstrip("\n\r").split("\t")
            if len(parts) < 2 or not parts[0]:
                continue
            yield parts[0], parts[1], [g for g in parts[2:] if g]


def gmt_set_id(name):
    """Bader Lab names look like 'NAME%SOURCE%ID'. Return (source, id); fall back to the full name."""
    parts = name.split("%")
    if len(parts) >= 3:
        return parts[-2], parts[-1]
    return "", name
