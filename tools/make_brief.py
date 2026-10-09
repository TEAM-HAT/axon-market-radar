#!/usr/bin/env python3
"""Build brief.json, the public file the Market Radar widgets read.

Input is an export of the radar's database: a folder holding events/, companies/,
runs/ and meta/ sub-folders with one JSON document per file (the layout that
ArtifactData "list" with out_dir writes). Output is brief.json.

Usage: python3 tools/make_brief.py --export /path/to/export --out brief.json

The file is public. It carries market news only: no AXON lens tags, no
watchlist, no summaries or notes.
"""
import argparse
import datetime as dt
import glob
import json
import os
import re

RADAR_URL = "https://claude.ai/artifact/ApmPVY9n2xQFf4yP4qMbei"
TYPE_LABEL = {"Funding": "Funding", "M&A": "M&A", "License": "Licence", "Launch": "Launch",
              "Partnership": "Partnership", "Regulation": "Regulation"}
ONE = {"Funding": "funding round", "M&A": "M&A deal", "License": "licence", "Launch": "launch",
       "Partnership": "partnership", "Regulation": "regulatory action"}
MANY = {"Funding": "funding rounds", "M&A": "M&A deals", "License": "licences", "Launch": "launches",
        "Partnership": "partnerships", "Regulation": "regulatory actions"}
REGIONS = ["Europe", "Middle East", "North America", "Global"]


def load(folder):
    out = []
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        with open(path, encoding="utf-8") as f:
            doc = json.load(f)
        doc = doc.get("data", doc) if isinstance(doc, dict) and "data" in doc and isinstance(doc["data"], dict) else doc
        doc.setdefault("id", os.path.splitext(os.path.basename(path))[0])
        out.append(doc)
    return out


def day(s):
    try:
        return dt.date.fromisoformat(str(s)[:10])
    except (TypeError, ValueError):
        return None


REG_RULES = [
    (r"VARA", "VARA"), (r"FSRA|ADGM", "ADGM FSRA"), (r"DFSA", "DFSA"), (r"CBUAE", "CBUAE"), (r"CBB", "CBB"), (r"SAMA", "SAMA"),
    (r"ESMA", "ESMA"), (r"CSSF", "CSSF"), (r"AFM", "AFM"), (r"DNB", "DNB"), (r"MFSA", "MFSA"),
    (r"\bFCA\b|FINANCIAL CONDUCT AUTHORITY", "FCA"), (r"BAFIN", "BaFin"), (r"SO-FIT|FINMA|VQF", "Swiss SRO"),
    (r"HM TREASURY", "HM Treasury"), (r"NYDFS|NEW YORK STATE DEPARTMENT OF FINANCIAL", "NYDFS"),
    (r"\bOCC\b|COMPTROLLER OF THE CURRENCY", "OCC"), (r"FDIC", "FDIC"), (r"FEDERAL RESERVE|^FED$|\bFRB\b", "Federal Reserve"),
    (r"FINCEN", "FinCEN"), (r"CFTC", "CFTC"), (r"\bSEC\b|SECURITIES AND EXCHANGE COMMISSION", "SEC"),
    (r"TREASURY|OFAC", "US Treasury"), (r"FINTRAC", "FINTRAC"), (r"\bFCAC\b", "FCAC"), (r"BANK OF CANADA", "Bank of Canada"),
    (r"\bOSC\b|ONTARIO SECURITIES", "OSC"), (r"\bCSA\b|CANADIAN SECURITIES ADMINISTRATORS", "CSA"), (r"\bCIRO\b", "CIRO"),
    (r"CENTRAL BANK OF IRELAND|\bCBI\b", "CBI"), (r"BANK OF LITHUANIA|LIETUVOS BANKAS", "Bank of Lithuania"),
    (r"AUTORIT[EÉ] DES MARCH[EÉ]S FINANCIERS", "AMF"),
]
REG_WHERE = {
    "VARA": "Dubai", "ADGM FSRA": "Abu Dhabi", "DFSA": "Dubai, DIFC", "CBUAE": "UAE", "CBB": "Bahrain", "SAMA": "Saudi Arabia",
    "ESMA": "European Union", "CSSF": "Luxembourg", "AFM": "Netherlands", "DNB": "Netherlands", "MFSA": "Malta",
    "FCA": "United Kingdom", "HM Treasury": "United Kingdom", "BaFin": "Germany", "Swiss SRO": "Switzerland",
    "NYDFS": "New York", "OCC": "United States", "Federal Reserve": "United States", "FDIC": "United States",
    "FinCEN": "United States", "SEC": "United States", "CFTC": "United States", "US Treasury": "United States",
    "FINTRAC": "Canada", "FCAC": "Canada", "Bank of Canada": "Canada", "OSC": "Ontario", "CSA": "Canada", "CIRO": "Canada",
    "AMF": "France", "FIN-FSA": "Finland", "CBI": "Ireland", "CONSOB": "Italy", "CNMV": "Spain", "CySEC": "Cyprus",
    "Bank of Lithuania": "Lithuania", "FSMA": "Belgium", "KNF": "Poland", "BMA": "Bermuda", "JFSC": "Jersey",
}


def reg_key(r):
    if not r:
        return None
    s = str(r).upper().strip()
    for pattern, name in REG_RULES:
        if re.search(pattern, s):
            return name
    return str(r)


def move(e, cos):
    c = cos.get(e.get("company_id"))
    return {
        "id": e.get("id"),
        "date": e.get("date"),
        "type": e.get("type"),
        "type_label": TYPE_LABEL.get(e.get("type"), e.get("type")),
        "region": e.get("region"),
        "regulator": reg_key(e.get("regulator")),
        "company": (c or {}).get("name") or e.get("company_name"),
        "title": e.get("title"),
        "source_name": e.get("source_name"),
        "source_url": e.get("source_url"),
    }


def build(export):
    events = load(os.path.join(export, "events"))
    cos = {c["id"]: c for c in load(os.path.join(export, "companies"))}
    runs = load(os.path.join(export, "runs"))
    meta = {m["id"]: m for m in load(os.path.join(export, "meta"))}.get("app", {})
    run = max(runs, key=lambda r: str(r.get("run_at", ""))) if runs else {}

    end = day(run.get("window_end")) or dt.date.today()
    start = day(run.get("window_start")) or end - dt.timedelta(days=6)
    newest = sorted(events, key=lambda e: (str(e.get("date", "")), str(e.get("added", "")), e["id"]), reverse=True)
    in_window = [e for e in newest if day(e.get("date")) and start <= day(e["date"]) <= end]

    counts = {}
    for e in in_window:
        counts[e["type"]] = counts.get(e["type"], 0) + 1
    mix = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))

    # Most active companies: moves in the 30 days to the end of the window, then the most recent move.
    since30 = end - dt.timedelta(days=29)
    activity = {}
    for e in newest:
        d = day(e.get("date"))
        if not d or d > end:
            continue
        for cid in [e.get("company_id")] + list(e.get("related") or []):
            if cid in cos:
                a = activity.setdefault(cid, {"n": 0, "last": e})
                if d >= since30:
                    a["n"] += 1
    active = sorted(activity.items(), key=lambda kv: (kv[1]["n"], str(kv[1]["last"].get("date")), kv[0]), reverse=True)
    companies = [{
        "id": cid, "name": cos[cid]["name"], "segment": cos[cid].get("segment"), "country": cos[cid].get("hq_country"),
        "region": cos[cid].get("region"), "moves_30d": a["n"], "last_date": a["last"].get("date"),
        "last_title": a["last"].get("title"), "last_url": a["last"].get("source_url"),
    } for cid, a in active[:6]]

    rules = [e for e in newest if e.get("type") in ("License", "Regulation")]
    since365 = end - dt.timedelta(days=364)
    regs = {}
    for e in rules:
        d = day(e.get("date"))
        if not d or d < since365:
            continue
        k = reg_key(e.get("regulator")) or "Other"
        r = regs.setdefault(k, {"name": k, "where": REG_WHERE.get(k, ""), "licences": 0, "actions": 0})
        r["licences" if e["type"] == "License" else "actions"] += 1
    regulators = sorted(regs.values(), key=lambda r: (-(r["licences"] + r["actions"]), r["name"]))[:5]

    months = []
    y, m = end.year, end.month
    for _ in range(13):
        months.append(f"{y:04d}-{m:02d}")
        m -= 1
        if m == 0:
            y, m = y - 1, 12
    months.reverse()
    per_month = {k: 0 for k in months}
    for e in events:
        k = str(e.get("date", ""))[:7]
        if k in per_month:
            per_month[k] += 1
    last_day = (dt.date(end.year + (end.month == 12), end.month % 12 + 1, 1) - dt.timedelta(days=1)).day
    n = lambda *types: sum(1 for e in events if e.get("type") in types)
    first = min((day(e.get("date")) for e in events if day(e.get("date"))), default=end)

    headline = run.get("headline") or ""
    if "axon" in headline.lower():
        headline = (f"**{len(in_window)} {'move' if len(in_window) == 1 else 'moves'}** in the last 7 days"
                    + (f": {'; '.join(f'{c} {(ONE if c == 1 else MANY)[t]}' for t, c in mix)}." if mix else "."))

    return {
        "schema": 2,
        "updated_at": meta.get("updated_at") or run.get("run_at"),
        "next_run": meta.get("next_run"),
        "window_start": start.isoformat(),
        "window_end": end.isoformat(),
        "week": end.isocalendar()[1],
        "count": len(in_window),
        "new_today": int(run.get("new_events") or 0) if run.get("kind") == "daily" else 0,
        "headline": headline,
        "mix": [{"type": t, "n": c, "label": (ONE if c == 1 else MANY)[t]} for t, c in mix],
        "mix_text": " · ".join(f"{c} {(ONE if c == 1 else MANY)[t]}" for t, c in mix),
        "regions": {r: sum(1 for e in in_window if e.get("region") == r) for r in REGIONS},
        "groups": {
            "regulatory": sum(1 for e in in_window if e.get("type") in ("License", "Regulation")),
            "capital": sum(1 for e in in_window if e.get("type") in ("Funding", "M&A")),
            "commercial": sum(1 for e in in_window if e.get("type") in ("Launch", "Partnership")),
        },
        "moves": [move(e, cos) for e in in_window[:6]],
        "latest": [move(e, cos) for e in newest[:8]],
        "companies": companies,
        "licences": [move(e, cos) for e in rules[:6]],
        "regulators": regulators,
        "trends": {
            "since": f"{first.year:04d}-{first.month:02d}",
            "total": len(events),
            "licences": n("License"),
            "regulatory_actions": n("Regulation"),
            "commercial": n("Launch", "Partnership"),
            "capital": n("Funding", "M&A"),
            "months": [{"m": k, "n": per_month[k]} for k in months],
            "partial_last": end.day < last_day,
        },
        "radar_url": RADAR_URL,
    }


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--export", required=True)
    ap.add_argument("--out", default="brief.json")
    a = ap.parse_args()
    brief = build(a.export)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(brief, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"wrote {a.out}: {brief['count']} moves in {brief['window_start']}..{brief['window_end']}, "
          f"{len(brief['latest'])} latest, {len(brief['companies'])} companies, {len(brief['licences'])} licences")
