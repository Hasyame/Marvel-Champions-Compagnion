# Data sources

Everything here was verified against the live MarvelCDB API on **2026-08-01**.
Where a claim is an inference rather than an observation, it says so.

## MarvelCDB public API

Base: `https://marvelcdb.com/api/public/`. No authentication is needed for any
endpoint the app uses.

| Endpoint | Notes |
|---|---|
| `cards/` | Every card. 2191 cards, 3.1 MB (EN) / 3.3 MB (FR). |
| `cards/{pack_code}` | Cards of one pack. |
| `card/{card_code}` | One card. Codes look like `01001a`, `09014`, `60007`. |
| `packs/` | Every pack. 61 packs, 9 KB. |
| `factions/` | Faction list. Returns 200. |
| `decklist/{id}` | A published decklist. |
| `deck/{id}` | A personal deck, only if the owner enabled "Share your decks". |

`sets/`, `types/` and `taxonomies/` return **404**. They do not exist.

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

### Pack wave

Not on the packs endpoint either, but cards carry `pack_wave` and `pack_legacy`,
so wave is derivable from `/api/public/cards/`. Waves 1–10 are present. Four
packs have no wave on their cards: `core`, `fne`, `synthezoid`, `tt`. Treat the
derived value as a starting point for the curated file, not as authoritative.

### Scenario to required-modular-set relationships

Not exposed. This is the curated file that F2 (randomiser) depends on. Cards do
expose `card_set_type_name_code` with values `hero`, `hero_special`, `leader`,
`modular`, `villain`, which identifies *which* sets are modular but not *which
scenario requires which*.

## Packs not in MarvelCDB

Announced or pre-ordered products are absent from the API until MarvelCDB enters
them. As of 2026-08-01 this includes **Elektra**, **Iron Fist** and
**Shadowland**, which are on pre-order and have no `pack_code`.

Note the trap: `Elektra` and `Iron Fist` *do* appear in the card database, but
as ally cards (`60007` in `fne`, `09014` in `drs`) — not as packs. Matching a
collection entry by card name would silently produce the wrong pack.

The collection model therefore has to tolerate packs the API does not know about
yet, so they can be marked as owned before MarvelCDB catches up.

## Card images

Fetched from MarvelCDB at runtime and disk-cached by Coil. Never committed to
this repository.
