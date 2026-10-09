# Market Radar for Android

A small app that puts the AXON Market Radar on the home screen as widgets.

| Widget | Default size | Shows | Opens |
| --- | --- | --- | --- |
| Daily briefing | 4 x 2 | Moves in the last 7 days, what is new today, the headline | Briefing tab |
| Moves | 4 x 4 | The latest moves | Each move's source |
| Companies | 4 x 4 | The most active companies over 30 days | That company in the radar |
| Licences | 4 x 4 | The busiest regulators, then the newest licences and rules | Each move's source |
| Trends | 4 x 3 | The year's totals and moves per month | Trends tab |
| Dashboard | 4 x 5 | Everything: briefing, regions, latest moves, companies, regulators, trend | The tab you tap |

- Every widget resizes; lists show as many rows as fit.
- The widgets read `brief.json` from https://team-hat.github.io/axon-market-radar/ every hour. The daily sweep rewrites that file each morning, Riyadh time. Tap "Updated" on any widget to refresh it now.
- The brief is public market news only: no AXON tags and no watchlist.

Layouts are generated: edit `tools/gen_layouts.py`, then run `python3 tools/gen_layouts.py` from this folder.
`app/src/test/.../WidgetRenderTest.kt` renders every widget at several sizes into `app/build/widget-previews/`.

The installable file is `download/market-radar.apk` at the repository root.

Release builds are signed with a key that is kept outside this repository. Use the same key for every update, or the phone will refuse to install it over the old version.
