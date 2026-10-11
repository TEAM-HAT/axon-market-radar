#!/usr/bin/env python3
"""Build radar-data.json, the copy of data/ that the web portal publishes beside its page.

The portal (https://claude.ai/artifact/ApmPVY9n2xQFf4yP4qMbei) reads its own database live and lays these
files over it the same way make_brief.py does for radar.json, so the portal and the Android app show the
same moves. The file is public: market news only, with no AXON tags and no watchlist.

Usage: python3 tools/portal_data.py --out /path/to/radar-data.json

After changing anything in data/, rebuild this file and republish the portal with it.
"""
import argparse
import datetime as dt
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from make_brief import DATA_DIR, PRIVATE, RADAR_START, public, read_json  # noqa: E402


def leaks(x, path="$"):
    """Every private key anywhere in the data, with where it sits."""
    out = []
    if isinstance(x, dict):
        for k, v in x.items():
            if k in PRIVATE:
                out.append(f"{path}.{k}")
            out += leaks(v, f"{path}.{k}")
    elif isinstance(x, list):
        for i, v in enumerate(x):
            out += leaks(v, f"{path}[{i}]")
    return out


def build(data_dir):
    enrich = read_json(os.path.join(data_dir, "enrich.json"), {})
    moves = read_json(os.path.join(data_dir, "moves.json"), {})
    companies = read_json(os.path.join(data_dir, "companies.json"), {})
    orgs = read_json(os.path.join(data_dir, "orgs.json"), {})
    themes = read_json(os.path.join(data_dir, "themes.json"), {})
    return {
        "schema": 1,
        "built_at": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "radar_start": RADAR_START,
        "enrich": {k: public(v) for k, v in (enrich.get("moves") or {}).items()},
        "moves": [public(m) for m in (moves.get("moves") or [])],
        "companies": {"version": companies.get("version"), "companies": {k: public(v) for k, v in (companies.get("companies") or {}).items()}},
        "orgs": orgs.get("orgs") or {},
        "themes": themes.get("themes") or [],
    }


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--data", default=DATA_DIR)
    a = ap.parse_args()
    d = build(a.data)
    bad = leaks(d)
    if bad:
        sys.exit("private fields found, nothing written: " + ", ".join(bad[:10]))
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {a.out}: {len(d['moves'])} moves, {len(d['enrich'])} enrichments, "
          f"{len(d['companies']['companies'])} companies (version {d['companies']['version']}), "
          f"{len(d['orgs'])} organisations, {len(d['themes'])} themes, {os.path.getsize(a.out):,} bytes")
