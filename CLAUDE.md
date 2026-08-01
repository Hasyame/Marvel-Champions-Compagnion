# CLAUDE.md

Instructions for AI assistants working in this repository. Read this before
changing anything.

## Ground rules

1. **Git identity.** Commits are authored by `Hasyame <benoit.breul@gmail.com>`.
   Verify with `git config user.name` / `git config user.email` before the first
   commit of a session.
2. **No AI attribution, anywhere.** Never add `Co-Authored-By: Claude`,
   `Generated with Claude Code`, or any equivalent to commit messages, PR
   descriptions, file headers, or documentation. This is not negotiable.
3. **Conventional Commits**, in English, no emoji: `feat:`, `fix:`, `chore:`,
   `docs:`, `test:`, `refactor:`.
4. **Work in milestones.** Stop at the end of each (see Roadmap), summarise, and
   wait. Do not run ahead.
5. **Verify, don't assume.** Check current stable versions against Maven metadata
   before pinning them. Call the MarvelCDB API and read the real JSON before
   writing a client. If something you expected does not exist, say so rather than
   inventing a fallback.
6. **Features before polish.** Plain Material 3 defaults are fine until
   milestone 7.
7. **Language.** Code, comments, commits and docs in English. The app UI is
   bilingual French / English — every user-facing string goes in `strings.xml`
   and `values-fr/strings.xml` as it is written, never retrofitted.

## Commands

| Task | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| Everything CI runs | `./gradlew lintDebug testDebugUnitTest assembleDebug` |
| Download the card seed | `./gradlew fetchCardSeed` |

Lint runs with `warningsAsErrors = true`. Fix the warning; do not add a baseline.

`fetchCardSeed` writes `app/src/main/assets/seed/` (~15 MB, gitignored). Without
it the app still builds and runs — it just asks for a sync on first launch,
which is how CI builds.

## Toolchain

JDK 21 · Gradle 9.6.1 · AGP 9.3.1 · Kotlin 2.4.10 · compileSdk 37 · minSdk 28 ·
targetSdk 37. Versions live in `gradle/libs.versions.toml` only — never inline a
version in a build file.

Two things about AGP 9 that will bite you:

- AGP 9 has **built-in Kotlin support**. Applying `org.jetbrains.kotlin.android`
  alongside it is an error. The Compose and serialization compiler plugins are
  still applied separately.
- `defaultConfig.resourceConfigurations` is gone; use
  `androidResources.localeFilters`.

Two more traps that have already cost time:

- Robolectric 4.16.1 caps at SDK 36 while the app targets 37, so
  `app/src/test/resources/robolectric.properties` pins `sdk=36`.
- The configuration cache is on. A custom task must not touch `project` from
  inside `doLast` — resolve paths and values at configuration time.

## Architecture

Single `:app` module, MVVM with unidirectional data flow, offline first,
repository pattern, `StateFlow` for UI state, Hilt for DI. Package boundaries are
drawn so the module can be split into Gradle modules later without a rewrite.

```
com.hasyame.marvelchampions
  core/          designsystem/  shared theme and UI primitives
                 ui/            reusable composables
  data/          marvelcdb/     Retrofit client and DTOs
                 db/            Room entities, DAOs, FTS, migrations
                 repository/    the only thing ui/ talks to
                 sync/          WorkManager card refresh
                 backup/        the user-state bundle, SAF export/import, merge
  domain/        model/         pure Kotlin, no Room or Retrofit types
                 campaign/      campaign engine — no Android dependencies
                 randomizer/    draw logic — no Android dependencies
                 deeplink/      MarvelCDB URL parsing
  ui/            cards/ decks/ campaign/ randomizer/ settings/
```

`domain/` must stay free of Android dependencies so it is testable with plain
JUnit. That is where the campaign state machine, the randomiser and the URL
parser live, and those are the things that most need tests.

### Navigation

Navigation Compose 2.9.8 with type-safe `@Serializable` routes
(`ui/navigation/Routes.kt`). Each of the five tabs is a **nested graph** wrapping
a start destination; the nesting is what gives each tab an independent back
stack, via `saveState`/`restoreState` in
`ui/navigation/TopLevelNavigation.kt`.

`NavigationSuiteScaffold` chooses a bottom bar or a navigation rail from the
window size class, so phone and tablet share one code path.

Adding a top level destination means adding to `TopLevelDestination`, to
`Routes.kt`, to `MarvelChampionsNavHost`, and to `graphRouteInstance()`. Note
that the navigation bar holds at most five items — `TopLevelDestinationTest`
enforces this.

## How to add a pack

Pack **type** and **wave** are not in the MarvelCDB API (see
`docs/DATA_SOURCES.md`). Everything else comes from `/api/public/packs/`.

1. Add the entry to `app/src/main/assets/pack_metadata.json` with its real
   MarvelCDB `pack_code`, its type, and its wave.
2. Bump the expected count in `PackMetadataAssetTest`, which fails deliberately
   when MarvelCDB publishes a pack the curated file does not cover.
3. Nothing else. Do not hardcode pack codes in Kotlin.

Never invent a `pack_code`. Resolve it against `/api/public/packs/` and report
the mapping before committing it. A pack that MarvelCDB has not entered yet has
no code at all — `owned_packs` deliberately has no foreign key so the collection
can still hold it.

## How to add a campaign

Campaign templates are **data, not assets**. They contain verbatim campaign book
text, so they are imported by the user from device storage and are never
committed to this repository (`.gitignore` enforces this).

A new campaign is one JSON file validated against the template schema at load
time. If you find yourself writing Kotlin for a specific campaign, stop: the
declarative schema is supposed to cover it. The scenario handler registry exists
for genuinely bespoke mechanics only, and every use of it must be justified —
if the same shape appears twice, it belongs in the schema instead.

## Roadmap

1. ✅ Skeleton — Gradle, Hilt, Compose, five-tab navigation, CI
2. ✅ Data layer — Room, MarvelCDB client, sync, asset seeding, FTS
3. ✅ F5 card search + F1 collection
4. F2 randomiser
5. F4 decklists — 5a import and share target, 5b in-app deck builder
6. F3 campaign engine + Galaxy's Most Wanted
7. Polish

## Legal constraints on what may be committed

No card images. No card text dumps. No campaign book text. The card seed
(`app/src/main/assets/seed/`) is generated at build time and gitignored;
campaign templates are user-supplied at runtime. Keep the engine and the content
strictly separable so this stays a configuration choice.
