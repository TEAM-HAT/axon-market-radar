#!/usr/bin/env python3
"""Build brief.json, the public file the Market Radar widgets read.

Input is an export of the radar's database: a folder holding events/, companies/,
runs/ and meta/ sub-folders with one JSON document per file (the layout that
ArtifactData "list" with out_dir writes). Output is brief.json.

Usage: python3 tools/make_brief.py --export /path/to/export --out brief.json

It also writes radar.json beside brief.json: every move with its summary and
source, every company profile, the regulators and the trend, for the Android
app. Pass --full "" to skip it.

Both files are public. They carry market news only: no AXON lens tags and no
watchlist.

The repository's data/ folder adds structure the database does not hold yet
(see data/README.md): enrich.json lays parties, deal and round details,
partnership kinds and themes over existing moves; moves.json adds moves found
later, including the deal history before the daily radar began; companies.json
adds companies and corrects existing ones until the sweep has imported that
version; orgs.json and themes.json name the organisations and themes. Moves
dated before the radar began are marked "history" and kept out of the counts
and trends.
"""
import argparse
import datetime as dt
import glob
import json
import os
import re

RADAR_URL = "https://claude.ai/artifact/ApmPVY9n2xQFf4yP4qMbei"
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data")
RADAR_START = "2025-10-01"  # the daily radar's first day; earlier moves are deal history
PRIVATE = {"lens", "axon", "mark", "marks", "watch", "watchlist", "private", "note_for_axon"}
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


def read_json(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def public(d):
    """A copy without any private field, at any depth of plain dicts."""
    return {k: v for k, v in d.items() if k not in PRIVATE}


def load_all(export, data_dir=None):
    """The database export with the repository's data/ files laid over it.

    Returns (events, companies by id, runs, meta/app, extras) where extras holds the themes and organisations.
    """
    data_dir = DATA_DIR if data_dir is None else data_dir
    events = load(os.path.join(export, "events"))
    cos = {c["id"]: c for c in load(os.path.join(export, "companies"))}
    runs = load(os.path.join(export, "runs"))
    meta = {m["id"]: m for m in load(os.path.join(export, "meta"))}.get("app", {})

    enrich = read_json(os.path.join(data_dir, "enrich.json"), {}).get("moves", {})
    for e in events:
        patch = enrich.get(e["id"])
        if patch:
            e.update(public({k: v for k, v in patch.items() if k != "id"}))
    have = {e["id"] for e in events}
    for m in read_json(os.path.join(data_dir, "moves.json"), {}).get("moves", []):
        if m["id"] not in have:
            events.append(public(dict(m)))
            have.add(m["id"])

    # Until the sweep has imported this version of companies.json into the database, the file's records win.
    extra = read_json(os.path.join(data_dir, "companies.json"), {})
    if extra and str(meta.get("companies_imported") or "") < str(extra.get("version") or ""):
        for cid, patch in (extra.get("companies") or {}).items():
            patch = public(patch)
            if cid in cos:
                cos[cid].update(patch)
            else:
                cos[cid] = dict(patch, id=cid)

    for e in events:
        e["history"] = str(e.get("date") or "") < RADAR_START
        rel = [r for r in (e.get("related") or []) if r != e.get("company_id")]
        for p in e.get("parties") or []:
            pid = p.get("id")
            if pid in cos and pid != e.get("company_id") and pid not in rel:
                rel.append(pid)
        e["related"] = rel

    extras = {
        "themes": read_json(os.path.join(data_dir, "themes.json"), {}).get("themes", []),
        "orgs": read_json(os.path.join(data_dir, "orgs.json"), {}).get("orgs", {}),
    }
    return events, cos, runs, meta, extras


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
    "Latvijas Banka": "Latvia", "GFSC": "Gibraltar", "Estonian FIU": "Estonia", "MAS": "Singapore", "BSP": "Philippines",
    "Bank Indonesia": "Indonesia", "FSCA": "South Africa", "ASIC": "Australia", "AUSTRAC": "Australia",
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


def build(export, data_dir=None):
    events, cos, runs, meta, _ = load_all(export, data_dir)
    events = [e for e in events if not e["history"]]  # the brief, counts and trends cover the daily radar only
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


EVENT_FIELDS = ["id", "date", "date_precision", "type", "region", "title", "summary", "amount_usd_m", "jurisdiction",
                "company_id", "company_name", "related", "source_name", "source_url", "added",
                "parties", "round", "deal", "kind", "themes", "history"]
# Company fields that are public facts. The AXON lens tag is left out on purpose.
COMPANY_FIELDS = ["id", "name", "hq_city", "hq_country", "region", "segment", "founded", "website", "blurb",
                  "licenses", "funding", "status", "sources"]
ORG_FIELDS = ["id", "name", "kind", "hq_country", "website"]


def build_full(export, brief, data_dir=None):
    """Everything the app shows: all moves newest first, companies, regulators, themes, organisations and the trend."""
    events, cos, runs, meta, extras = load_all(export, data_dir)
    run = max(runs, key=lambda r: str(r.get("run_at", ""))) if runs else {}
    newest = sorted(events, key=lambda e: (str(e.get("date", "")), str(e.get("added", "")), e["id"]), reverse=True)

    moves = []
    stats = {cid: {"moves": 0, "last_date": None} for cid in cos}
    for e in newest:
        m = {k: e.get(k) for k in EVENT_FIELDS}
        m["type_label"] = TYPE_LABEL.get(e.get("type"), e.get("type"))
        m["regulator"] = reg_key(e.get("regulator"))
        m["company_name"] = (cos.get(e.get("company_id")) or {}).get("name") or e.get("company_name")
        m["related"] = [r for r in (e.get("related") or []) if r in cos]
        m["parties"] = [{k: p.get(k) for k in ("id", "name", "role")} for p in (e.get("parties") or [])]
        m["themes"] = list(e.get("themes") or [])
        m["history"] = bool(e.get("history"))
        for k in ("round", "deal", "kind"):
            if not m.get(k):
                m.pop(k, None)
        moves.append(m)
        for cid in [e.get("company_id")] + m["related"]:
            if cid in stats:
                stats[cid]["moves"] += 1
                stats[cid]["last_date"] = stats[cid]["last_date"] or e.get("date")

    companies = []
    for cid, c in cos.items():
        d = {k: c.get(k) for k in COMPANY_FIELDS}
        d["licenses"] = [dict(public(l), regulator_key=reg_key(l.get("regulator"))) for l in (c.get("licenses") or [])]
        d.update(stats[cid])
        companies.append(d)
    companies.sort(key=lambda c: (str(c["last_date"] or ""), c["moves"], c["name"] or ""), reverse=True)

    # Themes with how many moves each holds, and the organisations the moves name that the radar does not track.
    live = [e for e in events if not e["history"]]
    since90 = (day(brief.get("window_end")) or dt.date.today()) - dt.timedelta(days=89)
    themes = [{"id": t["id"], "name": t["name"], "what": t.get("what"),
               "moves": sum(1 for e in events if t["id"] in (e.get("themes") or [])),
               "moves_90d": sum(1 for e in live if t["id"] in (e.get("themes") or []) and (day(e.get("date")) or since90) >= since90)}
              for t in extras["themes"]]
    named = {p["id"] for m in moves for p in m["parties"]} - set(cos)
    orgs = {i: {k: o.get(k) for k in ORG_FIELDS} for i, o in extras["orgs"].items() if i in named}
    for m in moves:
        for p in m["parties"]:
            if p["id"] not in cos and p["id"] not in orgs:
                orgs[p["id"]] = {"id": p["id"], "name": p["name"], "kind": None, "hq_country": None, "website": None}

    regs = {}
    for e in newest:
        if e.get("type") not in ("License", "Regulation"):
            continue
        k = reg_key(e.get("regulator")) or "Other"
        r = regs.setdefault(k, {"name": k, "where": REG_WHERE.get(k, ""), "licences": 0, "actions": 0, "last_date": e.get("date")})
        r["licences" if e["type"] == "License" else "actions"] += 1
    regulators = sorted(regs.values(), key=lambda r: (-(r["licences"] + r["actions"]), r["name"]))

    history = [m for m in moves if m["history"]]
    keep = ("updated_at", "next_run", "window_start", "window_end", "count", "new_today", "headline", "mix_text",
            "regions", "groups", "trends", "radar_url")
    return dict(
        {"schema": 2, "generated_at": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()},
        **{k: brief.get(k) for k in keep},
        radar_since=RADAR_START,
        history_since=min((m["date"] for m in history), default=None),
        highlights=[h for h in (run.get("highlights") or []) if "axon" not in str(h).lower()],
        events=moves, companies=companies, regulators=regulators, themes=themes, orgs=orgs,
    )


def check_public(full):
    """Warn about anything in the public file that looks like it belongs to the private lens."""
    problems = []
    for m in full["events"]:
        for k in ("title", "summary"):
            if re.search(r"\baxon\b", str(m.get(k) or ""), re.I):
                problems.append(f"move {m['id']}: {k} mentions AXON")
    for c in full["companies"]:
        if re.search(r"\baxon\b", str(c.get("blurb") or ""), re.I):
            problems.append(f"company {c['id']}: blurb mentions AXON")
    for p in problems:
        print("WARNING:", p)
    return problems


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--export", required=True)
    ap.add_argument("--out", default="brief.json")
    ap.add_argument("--full", default=None, help="path for radar.json (default: beside --out; empty to skip)")
    ap.add_argument("--data", default=None, help="the data/ folder with enrich, moves, companies, orgs and themes")
    a = ap.parse_args()
    brief = build(a.export, a.data)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(brief, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"wrote {a.out}: {brief['count']} moves in {brief['window_start']}..{brief['window_end']}, "
          f"{len(brief['latest'])} latest, {len(brief['companies'])} companies, {len(brief['licences'])} licences")
    full_path = os.path.join(os.path.dirname(os.path.abspath(a.out)), "radar.json") if a.full is None else a.full
    if full_path:
        full = build_full(a.export, brief, a.data)
        check_public(full)
        with open(full_path, "w", encoding="utf-8") as f:
            json.dump(full, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")
        hist = sum(1 for m in full["events"] if m["history"])
        print(f"wrote {full_path}: {len(full['events'])} moves ({hist} from the deal history before {RADAR_START}), "
              f"{len(full['companies'])} companies, {len(full['regulators'])} regulators, {len(full['themes'])} themes, "
              f"{len(full['orgs'])} other organisations")
