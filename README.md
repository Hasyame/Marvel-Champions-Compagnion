# Marvel Champions Companion

An Android companion app for **Marvel Champions: The Card Game**: card database and search, deck lists, a scenario randomiser driven by the
packs you actually own, and a data-driven campaign tracker. Offline first, no
account, no backend, no advertising.

Bilingual throughout — the interface and the card text are chosen separately, so
you can read the app in French and the cards in English, or the reverse.

## Status

In use, and being played with. The core is complete and the visual pass is
under way.

### Done

- **Card database.** The full MarvelCDB catalogue including encounter cards,
  searchable offline with accent-insensitive prefix matching, filters for type,
  aspect, cost and traits, and a detail screen showing which product a card
  comes from and which encounter set it belongs to. A card MarvelCDB has not
  translated is shown in English rather than hidden.
- **Collection.** Mark the packs you own; the randomiser and deck legality
  follow from it.
- **Decks.** Import from a MarvelCDB link or a share from the browser, build
  from scratch, edit, and check legality as you go.
- **Randomiser.** Scenario, difficulty, modular sets, heroes and aspects, drawn
  only from what you own, with locking, rerolls and a history.
- **Campaign tracker.** An append-only event log with all state folded from it,
  so a record can never drift from what was played. Counters, flags, card lists,
  a market, per-scenario questionnaires, and setup steps for one scenario that
  depend on what was recorded in the ones before it. The Galaxy's Most Wanted
  ships with the app.
- **Finished campaigns.** Saved runs keep total time, victory points, heroes,
  credits and a per-scenario log of the answers given.

### Next

- **The Mad Titan's Shadow** — campaign template
- **Age of Apocalypse** — campaign template
- **Fear No Evil** — campaign template
- Finishing the visual pass on the deck screens
- Signed release builds

Campaigns are added after they have been played, so that the mechanics in the
template come from experience rather than from a reading of the book.

## Requirements

- JDK 21
- Android SDK with platform API 37
- An Android device or emulator on API 28 (Android 9) or later

## Build

```bash
./gradlew assembleDebug
```

```bash
./gradlew lintDebug testDebugUnitTest
```

`local.properties` is not committed; create it with your SDK path, or set
`ANDROID_HOME`.

### Release signing

Without a keystore the release build falls back to the **debug** key and says so
loudly. That is fine for testing on your own device and must never be
distributed: a properly signed build cannot upgrade over a debug-signed one, so
installing the real thing later means uninstalling first, which erases every
campaign, deck and collection setting on the device.

Create a key once:

```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias mcc
```

Then copy `keystore.properties.example` to `~/.mcc/keystore.properties` and fill
it in, keeping `release.jks` beside it.

**Outside the repository on purpose.** A signing password has to be plain text
for Gradle to use it, so the protection is location: inside the project folder
the key is one zipped folder, one cloud backup or one bad `.gitignore` edit away
from being shared by accident. The build also accepts
`$MCC_KEYSTORE_PROPERTIES` pointing at any path, and still reads
`keystore.properties` in the repo root if you prefer that.

**Back the keystore up somewhere other than this machine.** It cannot be
regenerated or recovered. Losing it means no future build can ever update an
installed copy of the app.

Verify which key a build actually used:

```bash
keytool -printcert -jarfile app/build/outputs/apk/release/app-release.apk
```

A debug-signed build shows `CN=Android Debug`.

## Card data

Card and pack data comes from the [MarvelCDB](https://marvelcdb.com) public API,
maintained by its contributors. The snapshot bundled into the APK is generated
at build time and is **not** committed to this repository — see
[docs/DATA_SOURCES.md](docs/DATA_SOURCES.md).

## Licence

The source code is released under the [MIT Licence](LICENSE).

That licence covers **this code only**. It grants no rights whatsoever over
Marvel Champions: The Card Game, its cards, artwork, rules or campaign books,
none of which are mine to license — see below.

## Legal

This is an unofficial, non-commercial fan project. It is free, not for sale, and
carries no advertising.

Marvel Champions: The Card Game is © Marvel and published by Fantasy Flight
Games. This project is not affiliated with, endorsed by, or sponsored by Marvel,
Fantasy Flight Games or Asmodee. All trademarks and copyrights belong to their
respective owners.

No card images, card text, or campaign book text is stored in this repository.
Card data is fetched from MarvelCDB at build or run time and cached on the
device.

Campaign templates in `app/src/main/assets/campaigns/` hold **mechanics only** —
card codes, counters, conditions and effects, plus short labels and a
two-sentence blurb per scenario, all written for this app. They contain no rules
text and reproduce no text from the campaign book. They are a play aid for
someone who already owns the campaign box and has the book to hand; on their own
they do not explain how to play a campaign, and they are not a substitute for
either the book or the game.

The app collects nothing. There is no account, no analytics, no crash reporting
and no backend of any kind; everything it stores stays on the device.
