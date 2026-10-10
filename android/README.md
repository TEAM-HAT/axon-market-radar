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

## Logo

The Radar logo is an R inside radar rings, with the "Radar" wordmark beside it, white on the brand blue `#061AD3`.
It is the launcher icon (the white mark on blue), opens the app on that blue while the first data loads, heads the
Radar tab, and marks the deck's top line and every widget header. The widgets' blue surfaces use the same blue.
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

| Widget | Default size | Shows | Opens |
| --- | --- | --- | --- |
| Daily briefing | 4 x 2 | Moves in the last 7 days, what is new today, the headline | Briefing |
| Moves | 4 x 4 | The latest moves | That move |
| Companies | 4 x 4 | The most active companies over 30 days | That company |
| Licences | 4 x 4 | The busiest regulators, then the newest licences and rules | That move |
| Trends | 4 x 3 | The year's totals and moves per month | Trends |
| Dashboard | 4 x 5 | Briefing, regions, latest moves, companies, regulators, trend | The part you tap |
| Carousel | 4 x 4 | The app's cards as a list, made shorter: the name, then the kind, figure, title and date | Scroll the list like any list; tap a card to read it; up and down scroll a card at a time; the grid opens the app's lists |

Every widget resizes; lists show as many rows as fit. Tap "Updated" on a widget to refresh it now.
The carousel is a plain list, so it scrolls and flings the way any list does. Each card is the app's card made
shorter: the empty space in its black block is gone and the figure is a little smaller (52sp rather than 64sp).
Widgets cannot use the app's typeface, so the parts that carry it, the top line and each card's name and figure, are
drawn by `CardArt` with Inter Tight, set the way the app sets them, and the names and figures come as alpha masks the
widget tints, which keeps every card light. From Android 12 all the cards travel inside the widget itself
(`RemoteCollectionItems`), so the list never waits for a card to load as it scrolls; the cards take well under half
of the memory Android allows a widget, and if a launcher refuses, the list is cut down until it is taken. Before
Android 12, `CarouselService` hands the same cards to the launcher. Each placed widget stays where it was scrolled to
and goes back to the top when a new deck arrives.
`CardArtTest` sets the top line and the names and figures of a few cards for the app's own screen and compares them
pixel by pixel with the app's deck: they match to within one level in 255, in colour and in black and white.
`CarouselScrollTest` scrolls the widget with real touch events: the list follows the finger exactly, flings on after
a flick, rests where it is let go, scrolls a card at a time from the arrows, and opens a card when tapped. It saves a
clip of a drag in `app/build/carousel-clip/`.
The widget picker shows `res/drawable-nodpi/widget_carousel_preview.png`; to redraw it from the current data, run
`RADAR_WRITE_PREVIEW=1 gradle :app:testDebugUnitTest --tests '*WidgetRenderTest.picturesTheCarouselForThePicker'`.
The widgets and the app check for new data every hour.

## Building

The logo drawables (`logo_mark`, `logo_lockup`, `ic_launcher_foreground`, `mark`, `mark_solid`) are generated by
`python3 brand/build_logo.py` from the repository root; run it before `gen_layouts.py` if the logo changes.
Widget layouts are generated: edit `tools/gen_layouts.py`, then run `python3 tools/gen_layouts.py` from this folder.
It writes each layout and its black-and-white twin (`widget_*_mono.xml`, same ids), the `mono_*` drawables and
`values/colors_mono.xml`, so never edit those by hand.
`WidgetRenderTest` renders every widget into `app/build/widget-previews/` and `widget-previews-mono/`; `AppRenderTest`
renders every app screen into `app/build/app-previews/` and `app-previews-mono/`, all from the published data files
in the repository root.

The installable file is `download/market-radar.apk` at the repository root. Release builds are signed with a key kept
outside this repository. Use the same key for every update, or the phone will refuse to install it over the old version.
