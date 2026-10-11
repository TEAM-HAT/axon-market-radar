# Market Radar data

These files add structure and history to the moves the daily sweep collects. They are public: market news only, with no AXON tags and no watchlist. Anything private stays out of this repository.

`tools/make_brief.py` lays them over the database export when it writes `brief.json` and `radar.json`.

| File | What it holds |
| --- | --- |
| `themes.json` | The 15 market themes every move is filed under. |
| `orgs.json` | Investors, banks, networks and other organisations that moves name but the radar does not track as companies. |
| `enrich.json` | Extra fields for moves already in the database: parties, round and deal details, partnership kind and themes, plus corrections. Applied every time; the database record is never overwritten. |
| `moves.json` | Moves the database does not hold: the deal history before the daily radar began on 1 October 2025, and moves inside the radar's window that the daily sweep missed. |
| `companies.json` | New company profiles and corrections to existing ones. While `meta/app.companies_imported` in the database is older than this file's `version`, the file's records win. |

## Moves

A move dated before 1 October 2025 is history. It shows on company pages and in the deal history, but it never counts towards the daily brief, the weekly mix or the trend since October 2025.

Fields beyond the database's own:

- `parties`: every organisation named, each with an `id`, the `name` the source used and a `role`: lead investor, investor, buyer, target, seller, partner, client, supplier, issuer or custodian. An `id` is either a tracked company or an entry in `orgs.json`.
- `round` (funding): `stage` (Pre-seed, Seed, Seed extension, Pre-Series A, Series A to F, Growth, Strategic, Private placement, Token sale, Debt, IPO or Undisclosed), `valuation_usd_m` and `use_of_funds`.
- `deal` (M&A): `status` (announced, completed, called off or reported), `valuation_usd_m`, `rationale` and `gains` (licences, clients, technology, team, distribution, market entry).
- `kind` (partnerships): Bank adoption, Consumer distribution, Card programme, Payouts and corridors, Merchant acceptance, Venue listing, Infrastructure supply, Network or consortium, or Enterprise integration.
- `themes`: one or more theme ids from `themes.json`.

Every move carries a source link. Amounts are in millions of US dollars at the rate on the day of the announcement.

## Rules

- Public facts only, each with its source. No AXON lens, marks, notes or watchlist in any file.
- British spelling (licence, authorised, programme, tokenised).
- Titles up to 70 characters and summaries up to 260.
- When the daily sweep finds a move already in `moves.json`, it keeps the existing id rather than adding a duplicate.
