#!/usr/bin/env python3
"""List the organisations that keep turning up in the radar's moves but are not tracked companies yet.

The Sunday new-company scan in the daily sweep uses it. Firms named as a partner, client, supplier, buyer, target,
issuer or custodian in several moves are candidates for the radar; investors named in several rounds are listed apart,
as the funds whose new rounds the scan should look for. Public data only (radar.json).

Usage: python3 tools/candidates.py [--radar radar.json] [--min 3]
"""
import argparse
import json
import os
import signal
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--radar", default=os.path.join(HERE, "..", "radar.json"))
    ap.add_argument("--min", type=int, default=3)
    a = ap.parse_args()
    with open(a.radar, encoding="utf-8") as f:
        r = json.load(f)
    tracked = {c["id"] for c in r.get("companies") or []}
    seen = defaultdict(lambda: {"names": set(), "roles": set(), "moves": []})
    for e in r.get("events") or []:
        for p in e.get("parties") or []:
            pid = p.get("id")
            if not pid or pid in tracked:
                continue
            x = seen[pid]
            x["names"].add(p.get("name") or pid)
            if p.get("role"):
                x["roles"].add(p["role"])
            x["moves"].append((str(e.get("date") or ""), e.get("title") or e.get("id")))
    rows = [(pid, x) for pid, x in seen.items() if len(set(x["moves"])) >= a.min]
    rows.sort(key=lambda kv: (-len(set(kv[1]["moves"])), kv[0]))
    money = {"investor", "lead investor"}
    firms = [(pid, x) for pid, x in rows if x["roles"] - money]
    funds = [(pid, x) for pid, x in rows if not (x["roles"] - money)]
    for title, group in ((f"Firms in {a.min} or more moves that are not tracked companies (candidates for the radar)", firms),
                         (f"Investors in {a.min} or more rounds (search their new rounds on Sundays)", funds)):
        print(f"{title}: {len(group)}")
        for pid, x in group:
            moves = sorted(set(x["moves"]), reverse=True)
            print(f"- {pid} ({' / '.join(sorted(x['names']))}): {len(moves)} moves as {', '.join(sorted(x['roles'])) or 'party'}; "
                  f"latest {moves[0][0]}: {moves[0][1]}")
        print()


if __name__ == "__main__":
    signal.signal(signal.SIGPIPE, signal.SIG_DFL)
    main()
