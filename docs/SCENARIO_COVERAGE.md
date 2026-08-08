# Scenario coverage

Benoit listed every scenario in the game, from the boxes, in August 2026. Cross
checking that against MarvelCDB found something the app cannot fix by trying
harder: **19 scenarios exist in the game and not in MarvelCDB.**

The app derives its scenario pool from villain encounter sets in the card
database, so a scenario MarvelCDB has not entered does not exist as far as the
app is concerned. No amount of query fixing changes that.

## Missing from MarvelCDB, so missing from the app

| Pack | Scenarios | Count |
|---|---|---|
| Civil War | Resistance and Registration, for Captain Marvel, Iron Man, Spider-Woman and Captain America | 8 |
| She-Hulk | Resistance: She-Hulk, Registration: She-Hulk | 2 |
| Fear No Evil | The Getaway, Protection Racket, The Raft Breakout, Kingpin, Bullseye, Electro | 6 |
| Shadowland | Shadows in the Night, Shadow Labyrinth, Heart of Shadow | 3 |

That is waves 9 to 11, largely. MarvelCDB enters cards as volunteers get to
them, and the newest boxes lag.

## Present in MarvelCDB, verified

Everything else on the list resolves, including both Green Goblin scenarios
(`risky_business`, `mutagen_formula`, pack `gob`). One naming nit: MarvelCDB
spells M.O.D.O.K. without the trailing dot.

## Civil War, built

A Civil War game is a **pair**: a side, Resistance or Registration, and the hero
you face. Those eight combinations are the playable units — a villain on its own
is not a game — and MarvelCDB has entered none of them. It has entered all
fifteen modular sets, though, which meant they were drawable for every other
scenario in the collection.

Both halves are now in:

- The pairs are offered from `curated_scenarios.json`, generated from the box
  list. The app can name the game and cannot show a setup, because there are no
  card codes for it. For a whole campaign box that is a better trade than
  pretending it does not exist.
- Their modular sets are legal only in Civil War and She-Hulk games, and no
  longer legal anywhere else. The check runs both ways; without the second half
  Hell's Kitchen turned up in Rhino.
- Three or four sets per game, decided by the draw rather than fixed.

## What this means for the pool

The choice is between a pool derived from the card database, which is always
correct about what the app can show cards for and always behind on what exists,
and a curated list, which is complete but cannot name a single card for the
scenarios MarvelCDB lacks.

A curated list would let the randomiser offer "Kingpin" — but the app could not
show its villain deck, its main scheme, or its encounter sets, because it has no
codes for them. Offering a scenario the app then cannot set up may be worse than
not offering it. Undecided, deliberately.
