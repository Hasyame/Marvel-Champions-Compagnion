# Marvel Champions Companion

An Android companion app for **Marvel Champions: The Card Game**, built for solo
play: card database and search, deck lists, a scenario randomiser driven by the
packs you actually own, and a data-driven campaign tracker. Offline first, no
account, no backend.

## Status

Milestone 1 of 7 — project skeleton. Five navigation destinations exist and are
empty. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the plan.

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

## Card data

Card and pack data comes from the [MarvelCDB](https://marvelcdb.com) public API.
The snapshot bundled into the APK is generated at build time and is **not**
committed to this repository — see [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md).

## Legal

This is an unofficial, non-commercial fan project. It is not for sale and not
distributed through any app store.

Marvel Champions: The Card Game is © Marvel and published by Fantasy Flight
Games. This project is not affiliated with, endorsed by, or sponsored by Marvel
or Fantasy Flight Games.

No card images, card text, or campaign book text is stored in this repository.
Card data is fetched from MarvelCDB at build or run time and cached on the
device. Campaign templates contain verbatim campaign book text and are imported
by the user from their own device storage; they are never committed here.
