# Market Radar for Android

The AXON Market Radar as a full Android app, plus home-screen widgets. It reads the public
`radar.json` that the daily sweep publishes to https://team-hat.github.io/axon-market-radar/ (the sweep also
publishes `brief.json`, which versions before 3.9 read for their widgets).
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

## Logo

The Radar logo is an R inside radar rings, with the "Radar" wordmark beside it, white on the brand blue `#061AD3`.
It is the launcher icon (the white mark on blue), opens the app on that blue while the first data loads, heads the
Radar tab, and marks the deck's top line, on the app's deck and on the widgets that start with it.
`brand/README.md` at the repository root describes how the mark is built and lists the logo files.

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

Each widget is a piece of the app, in the app's colours and type: the briefing on black like the deck, the
directories on their light grey, and the licences on the licence colour, as a move's own page.

| Widget | Default size | Shows | Opens |
| --- | --- | --- | --- |
| Daily briefing | 4 x 3 | On black: the app's top line, the week's count, the headline, the week's mix by kind, and the latest 10 moves to scroll | That move; the count and headline open the briefing |
| Moves | 4 x 4 | The newest three as the app's tiles, then the latest 50 moves under their months | That move |
| Companies | 4 x 4 | The 30 most active companies of the last 30 days as the app's Watching boxes, each in its latest move's colour | That company |
| Licences | 4 x 4 | On the licence colour: the busiest regulators as black and white tiles, then the latest licences and rules under their months | That move |
| Trends | 4 x 3 | Moves since the radar began, the four totals, and moves per month stacked by kind | Trends |
| Dashboard | 4 x 5 | The briefing on black, then the latest moves, the most active companies, the busiest regulators and moves per month, as many as fit | The part you tap |
| Carousel | 4 x 4 | The app's cards as a list, made shorter: the name, then the kind, figure, title and date | Scroll the list like any list; tap a card to read it; up and down scroll a card at a time; the grid opens the app's lists |

Every widget follows the look chosen on the Radar tab and resizes: lists scroll, and the briefing, trends and
dashboard measure themselves as the launcher will lay them out, showing what fits (the briefing's list from about
one row of room, the chart in whatever is left). Tap ↻ on a widget to check for news now.
Widgets cannot use the app's typeface, so the parts set in it (headings, counts, names, figures and the top line) are
drawn by `Letters`, which sets Inter Tight the way the app's Compose text does, and come as alpha masks the widget
tints. `Kit` holds the shared parts: rows, tiles, boxes, month headers, the mix bar and the month chart. From Android
12 a widget's list travels inside the widget itself (`RemoteCollectionItems`), so it never waits for a row to load;
before that, `ListsService` and `CarouselService` hand the same items to the launcher.

The carousel is a plain list, so it scrolls and flings the way any list does. Each card is the app's card made
shorter: the empty space in its black block is gone and the figure is a little smaller (52sp rather than 64sp).
The cards take well under half of the memory Android allows a widget, and if a launcher refuses, the list is cut down
until it is taken. Each placed carousel stays where it was scrolled to and goes back to the top when a new deck arrives.
`CardArtTest` sets the top line and the names and figures of a few cards for the app's own screen and compares them
pixel by pixel with the app's deck: they match to within one level in 255, in colour and in black and white.
`CarouselScrollTest` scrolls the widget with real touch events: the list follows the finger exactly, flings on after
a flick, rests where it is let go, scrolls a card at a time from the arrows, and opens a card when tapped. It saves a
clip of a drag in `app/build/carousel-clip/`.
`WidgetListTest` scrolls every list and taps its rows and boxes, checks the dashboard and trends never run off the
bottom, and sends every widget through a parcel as the launcher receives it.
The widget picker shows `res/drawable-nodpi/widget_*_preview.png`; to redraw them from the current data, run
`RADAR_WRITE_PREVIEW=1 gradle :app:testDebugUnitTest --tests '*WidgetRenderTest.picturesEveryWidgetForThePicker'`.
The widgets and the app check for new data every hour.

## Building

The logo drawables (`logo_mark`, `logo_lockup`, `ic_launcher_foreground`, `mark`, `mark_solid`) are generated by
`python3 brand/build_logo.py` from the repository root; run it before `gen_layouts.py` if the logo changes.
Widget layouts are generated: edit `tools/gen_layouts.py`, then run `python3 tools/gen_layouts.py` from this folder.
It writes the layouts and the widgets' drawables (grounds, rows, swatches, tiles, cards and their fine lines), so
never edit those by hand. One layout serves both looks: every colour that differs is set from code.
`WidgetRenderTest` renders every widget at several sizes into `app/build/widget-previews/` and `widget-previews-mono/`,
and as on a 400dp phone with text at 85% into `widget-previews-phone/`; `AppRenderTest`
renders every app screen into `app/build/app-previews/` and `app-previews-mono/`, all from the published data files
in the repository root.

The installable file is `download/market-radar.apk` at the repository root. Release builds are signed with a key kept
outside this repository. Use the same key for every update, or the phone will refuse to install it over the old version.
