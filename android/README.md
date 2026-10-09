# Market Radar for Android

The AXON Market Radar as a full Android app, plus home-screen widgets. It reads the public
`radar.json` and `brief.json` that the daily sweep publishes to https://team-hat.github.io/axon-market-radar/.
Public market news only: no AXON tags and no watchlist.

## The app

- **Briefing**: the latest moves as a deck of flat colour cards on black. Drag to flip through; tap the open card to read it.
- **Move pages**: one full colour page per move, coloured by kind: licence yellow, rule blue, funding crimson, M&A violet,
  launch sage, partnership slate. Huge name, the figure block, Read the source and Watch, the statement, and the company's
  moves along the bottom. Swipe sideways between moves.
- **Explore**: Moves, Companies and Licences, each with a carousel, filters with counts and plain rows.
- **Company pages**: the same layout, with licences, funding and every move.
- **Watching**: companies you follow, kept on the phone.
- **Trends** and **Radar** (data status, refresh, link to the web radar).

The design follows the clip Hamed shared: Swiss-style type (Inter Tight, SIL Open Font License), flat colour pages,
a stacked card deck and a light directory.

## Widgets

| Widget | Default size | Shows | Opens |
| --- | --- | --- | --- |
| Daily briefing | 4 x 2 | Moves in the last 7 days, what is new today, the headline | Briefing |
| Moves | 4 x 4 | The latest moves | That move |
| Companies | 4 x 4 | The most active companies over 30 days | That company |
| Licences | 4 x 4 | The busiest regulators, then the newest licences and rules | That move |
| Trends | 4 x 3 | The year's totals and moves per month | Trends |
| Dashboard | 4 x 5 | Briefing, regions, latest moves, companies, regulators, trend | The part you tap |

Every widget resizes; lists show as many rows as fit. Tap "Updated" on a widget to refresh it now.
The widgets and the app check for new data every hour.

## Building

Widget layouts are generated: edit `tools/gen_layouts.py`, then run `python3 tools/gen_layouts.py` from this folder.
`WidgetRenderTest` renders every widget into `app/build/widget-previews/`; `AppRenderTest` renders every app screen
into `app/build/app-previews/`, both from the published data files in the repository root.

The installable file is `download/market-radar.apk` at the repository root. Release builds are signed with a key kept
outside this repository. Use the same key for every update, or the phone will refuse to install it over the old version.
