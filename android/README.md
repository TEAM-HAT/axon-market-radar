# Market Radar for Android

A small app that puts the AXON Market Radar weekly brief on the home screen.

- Two widgets: **Weekly brief** (4 x 2) and **Brief and top moves** (4 x 4).
- The widgets read `brief.json` from https://team-hat.github.io/axon-market-radar/ every three hours. The Sunday sweep rewrites that file.
- Tapping the app icon or a widget opens the live radar in Claude. Tapping a move opens its source.

The installable file is `download/market-radar.apk` at the repository root.

Release builds are signed with a key that is kept outside this repository. Use the same key for every update, or the phone will refuse to install it over the old version.
