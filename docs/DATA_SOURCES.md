# Data sources

Everything here was verified against the live MarvelCDB API on **2026-08-01**.
Where a claim is an inference rather than an observation, it says so.

## MarvelCDB public API

Base: `https://marvelcdb.com/api/public/`. No authentication is needed for any
endpoint the app uses.

| Endpoint | Notes |
|---|---|
| `cards/?encounter=1` | Every card. **4375** cards, 7.25 MB (EN) / 7.62 MB (FR). |
| `cards/` | Player cards only — **2086**. See the warning below. |
| `cards/{pack_code}` | Cards of one pack. Unaffected by `encounter`. |
| `card/{card_code}` | One card. Codes look like `01001a`, `09014`, `60007`. |
| `packs/` | Every pack. 61 packs, 9 KB. |
| `factions/` | Faction list. Returns 200. |
| `decklist/{id}` | A published decklist. |
| `deck/{id}` | A personal deck, only if the owner enabled "Share your decks". |

`sets/`, `types/` and `taxonomies/` return **404**. They do not exist.

### The `encounter=1` trap

`GET /api/public/cards/` returns **2086** cards. `GET /api/public/cards/?encounter=1`
returns **4375** — the same list plus every villain, main scheme, side scheme,
minion, treachery, attachment and modular set card.

There is no error, no warning and no pagination hint. The bare endpoint just
silently omits more than half the database, which would leave the randomiser
and the campaign tracker with nothing to work from.

`sum(known)` over `/packs/` is 4375, which is the check that caught it. **Always
pass `encounter=1`.** The per-pack endpoint returns everything either way.

### Caching

Responses carry `Cache-Control: max-age=600, public` and a `Last-Modified`
header. `Last-Modified` on `cards/` was `Thu, 23 Jul 2026 12:07:56 GMT`. Use
conditional requests for the manual refresh in Settings.

## Localisation — the mechanism is the locale subdomain

Three candidate mechanisms were tested against `card/01001a`:

| Mechanism | Result |
|---|---|
| `https://fr.marvelcdb.com/api/public/…` | **Works.** `Content-Language: fr`, translated payload. |
| `?_locale=fr` query parameter | Ignored. `Content-Language: en`. |
| `Accept-Language: fr` header | Ignored. `Content-Language: en`. |

So the client varies the **host**, not the path or the headers.

What is translated: `name`, `text`, `traits`, `flavor`, `type_name`,
`faction_name`, `pack_name`, `card_set_name`.

Example (`01021`):

- EN — name `Gamma Slam`, traits `Attack. Superpower.`
- FR — name `Frappe Gamma`, traits `Attaque. Super-pouvoir.`

### Card fields

The full dump exposes **88 distinct top-level fields**. `CardDto` and
`CardEntity` carry all of them. Only the always-present ones are non-nullable:
the API omits null fields from top-level card objects rather than emitting them,
so almost everything has to be optional.

`meta`, `deck_options`, `deck_requirements`, `restrictions` and `duplicated_by`
are structured. They are stored as raw JSON strings — nothing interprets them
yet, and discarding them would lose data we cannot get back without a resync.

### `linked_card` is redundant

105 player cards (327 including encounter cards) carry a nested `linked_card`
object — a hero's alter-ego side, a scheme's reverse. **Every linked card also
appears as its own top-level entry in the same response**, so the nested copy is
dropped and the relationship is rebuilt from `linked_to_code`.

One thing that looks alarming and is not: nested objects are *dense* (they
include explicit nulls) while top-level entries are *sparse* (nulls omitted), so
a nested object appears to have dozens of fields its top-level twin lacks. It
does not. The only genuinely nested-only key is `id`, MarvelCDB's internal row
id, which nothing needs.

### Two things to know about the FR payload

1. **`real_name`, `real_text` and `real_traits` stay English** in the FR
   response. A single FR fetch therefore yields both languages for those three
   fields. It does *not* yield English `flavor` or `subname`, so the sync still
   fetches both locales.
2. **Fallback is server-side.** Both locales return exactly 2191 cards; an
   untranslated card comes back with English content rather than being absent.
   The client never has to implement a fallback.

### Translation coverage is incomplete

`fne` (Fear No Evil) is entirely untranslated as of 2026-08-01, and only 68 of
its 276 cards are entered at all. This is expected to improve; the app must not
assume a pack is fully populated.

## What the API does not give us

These require curated data. Do not guess them.

### Pack type

`/api/public/packs/` returns only:

```json
{ "name": "Core Set", "code": "core", "position": 1, "available": "2019-11-01",
  "known": 216, "total": 355, "url": "https://marvelcdb.com/set/core", "id": 1 }
```

There is **no type field**. Core set / hero pack / scenario pack / campaign box
must be maintained in the curated `pack_metadata.json`.

Classification in `assets/pack_metadata.json` was derived from card composition:
a pack with two hero sets and 150+ cards is a campaign box, one hero set and no
villain set is a hero pack, no hero set and a villain set is a scenario pack.

Six packs could not be classified that way and are marked `typeManual`:
`core`, `ron` (a five-card standalone modular set), and `cw`, `fne`,
`synthezoid`, `tt` — the four newest, whose card sets MarvelCDB has not finished
tagging.

### Pack wave

Not on the packs endpoint either, but cards carry `pack_wave` and `pack_legacy`,
so wave is derivable from `/api/public/cards/?encounter=1`. Waves 1–10 are
present.

Ten packs have no wave on any of their cards: `core`, `gob`, `twc`, `ron`,
`toafk`, `hood`, `mojo`, `tt`, `synthezoid`, `fne`. Their wave in the curated
file was inferred from release-date adjacency to the campaign box of that wave
and is marked `waveInferred`. These are the entries most likely to be wrong.

### Scenario to required-modular-set relationships

Not exposed as a field — **but it is written on the scenario's main scheme
card**, in the `Contents:` paragraph of `back_text`:

> Contents: … Rhino and Standard encounter sets. One modular encounter set
> (recommended: Bomb Scare).

`tools/generate-scenario-rules.mjs` parses that into
`app/src/main/assets/scenario_rules.json`. Only structured references are
written out — set codes, counts and flags, never the prose — so nothing there
republishes card text. Names come from the card database at runtime, already
localised.

Two things the parser depends on:

- The word is separated from the colon by markup: the raw field is
  `<b>Contents</b>:`, so **strip HTML before matching**, or a `Contents\s*:`
  regex silently matches nothing.
- A bare parenthetical names **mandatory** sets. One beginning `recommended:` is
  only a printed suggestion, so those sets stay in the random pool.

Coverage on 2026-08-01: 58 scenarios, **49 parsed cleanly, 9 flagged
`needsReview`** rather than guessed. Only one of the nine (`sinister_six`) is in
a pack the collection currently owns, and it genuinely uses no modular set.

Counts are not always small: The Hood asks for **seven** modular sets.

### Card sets are derivable, so only the relationship needs a file

`card_set_type_name_code` takes the values `hero`, `hero_special`, `leader`,
`modular`, `villain`, `nemesis`, `standard`, `expert`, `evidence`,
`main_scheme`. That is enough to derive the scenario list, the modular set list
and the hero list at runtime, filtered by owned packs — no curation needed for
any of them.

Difficulty sets present: `standard`, `standard_ii`, `standard_iii`,
`standard_pvp`, `expert`, `expert_ii`.

Aspects are the primary factions: `aggression`, `justice`, `leadership`,
`protection`, `pool`, plus `basic` which is not a chosen aspect. **`pool` is
Deadpool-only** — 34 cards, all in the `deadpool` pack — so the randomiser
must never offer it to another hero.

## Packs not in MarvelCDB

Announced or pre-ordered products are absent from the API until MarvelCDB enters
them. As of 2026-08-01 this includes **Elektra**, **Iron Fist** and
**Shadowland**, which are on pre-order and have no `pack_code`.

Note the trap: `Elektra` and `Iron Fist` *do* appear in the card database, but
as ally cards (`60007` in `fne`, `09014` in `drs`) — not as packs. Matching a
collection entry by card name would silently produce the wrong pack.

The collection model therefore has to tolerate packs the API does not know about
yet, so they can be marked as owned before MarvelCDB catches up.

## Deck building rules

Almost everything the deck builder needs is in the card data:

- `deck_limit` — copies allowed. Values seen: 1, 2, 3, 4, 6.
- `is_unique` — and **no unique card has `deck_limit` > 1**, so the two never
  disagree.
- `faction_code` — `hero`, `aggression`, `justice`, `leadership`, `protection`,
  `basic`, `pool`, plus `encounter` and `campaign` which are not player cards.

Two exceptions are encoded per hero, and **only two heroes have them**:

| Hero | `deck_requirements` |
|---|---|
| Spider-Woman (`04031a`) | `[{"aspects":2}]` |
| Adam Warlock (`21031a`) | `[{"aspects":4,"limit":1}]` |

**Five heroes** carry `deck_options`, which *widen* what is legal. Without
honouring them the builder rejects perfectly legal decks:

| Hero | Allowance |
|---|---|
| Gamora | 6 Attack/Thwart events of any aspect |
| Cyclops | X-Men allies of any aspect |
| Cable | player side schemes |
| Maria Hill | S.H.I.E.L.D. supports, name limit 3 |
| Wonder Man | events with an energy resource |

Beware when counting these: reading `cards.filter(c => c.deck_options)` gives 5,
but scanning every key occurrence gives ~330, because nested `linked_card`
objects include the key with an explicit `null`.

### The one rule that is not in the data

**Deck size appears nowhere.** MarvelCDB encodes copy limits, uniqueness,
factions and the per-hero exceptions, but nothing states how many cards a deck
must hold. Checked on 2026-08-01: of 72 hero cards, **zero** mention a deck
size, and no card in the whole pool mentions a 40- or 50-card deck.

So it is configured, not derived: `MINIMUM_DECK_SIZE = 40` and
`MAXIMUM_DECK_SIZE = 50` in `domain/deckbuilder/DeckRules.kt`, confirmed by the
owner as the rule they play by.

A hero that departs from those bounds goes in `HERO_DECK_SIZE_OVERRIDES` in the
same file. It is **empty on purpose** — no such hero is known, and inventing one
would be worse than having none. The map exists so that adding an exception is a
data change rather than a change to the validator.

## Card images

Fetched from MarvelCDB at runtime and disk-cached by Coil. Never committed to
this repository.
