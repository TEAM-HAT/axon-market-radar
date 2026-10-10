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
- **Watching**: the companies you follow as boxes in the colour of their latest move; a tap opens the company.
  **Add companies** opens a searchable list of every company on the radar to add or remove. Kept on the phone.
- **Trends** and **Radar** (data status, refresh, link to the web radar, and the look).

The design follows the clip Hamed shared: Swiss-style type (Inter Tight, SIL Open Font License), flat colour pages,
a stacked card deck and a light directory.

## Two looks

The Radar tab offers **Colour** and **Black & white**. The choice is kept on the phone and applies to the app and the
widgets at once; the screen crossfades from one look to the other.

Black and white keeps the same layout and gives each family of move one tone, the way the glyphs already group them:

| Family | Kinds | Tone | Glyph |
| --- | --- | --- | --- |
| Rules | Licence, regulation | Paper white `#F2F2F2` | Diamond |
| Capital | Funding, M&A | Black, with white type and white figure blocks | Square |
| Commercial | Launch, partnership | Silver `#B4B4B4` | Dot |

The second kind in each family (regulation, M&A, partnership) carries fine diagonal lines on its figure block and
squares, and a hollow glyph, so all six kinds stay apart without colour. Charts use the same tones and lines.
The colour look is unchanged.

## Widgets

| Widget | Default size | Shows | Opens |
| --- | --- | --- | --- |
| Daily briefing | 4 x 2 | Moves in the last 7 days, what is new today, the headline | Briefing |
| Moves | 4 x 4 | The latest moves | That move |
| Companies | 4 x 4 | The most active companies over 30 days | That company |
| Licences | 4 x 4 | The busiest regulators, then the newest licences and rules | That move |
| Trends | 4 x 3 | The year's totals and moves per month | Trends |
| Dashboard | 4 x 5 | Briefing, regions, latest moves, companies, regulators, trend | The part you tap |
| Carousel | 4 x 4 | The app's deck of cards, one card at a time with the next peeking in | Swipe to scroll through the cards; the arrows step one card; a card opens that move; the grid opens the app |

Every widget resizes; lists show as many rows as fit. Tap "Updated" on a widget to refresh it now.
The carousel's cards are a list the launcher scrolls under your finger (`CarouselService` supplies them). Each card is
sized to the widget so the next one peeks in, and its figure and text are fitted to that size. The widget picker shows
the stacked deck as its preview (`widget_carousel_preview.xml`).
The widgets and the app check for new data every hour.

## Building

Widget layouts are generated: edit `tools/gen_layouts.py`, then run `python3 tools/gen_layouts.py` from this folder.
It writes each layout and its black-and-white twin (`widget_*_mono.xml`, same ids), the `mono_*` drawables and
`values/colors_mono.xml`, so never edit those by hand.
`WidgetRenderTest` renders every widget into `app/build/widget-previews/` and `widget-previews-mono/`; `AppRenderTest`
renders every app screen into `app/build/app-previews/` and `app-previews-mono/`, all from the published data files
in the repository root.

The installable file is `download/market-radar.apk` at the repository root. Release builds are signed with a key kept
outside this repository. Use the same key for every update, or the phone will refuse to install it over the old version.
