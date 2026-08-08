# Scenario coverage

Benoit listed every scenario in the game, from the boxes, in August 2026. Cross
checking that against MarvelCDB found something the app cannot fix by trying
harder: **17 scenarios exist in the game and not in MarvelCDB.**

The app derives its scenario pool from villain encounter sets in the card
database, so a scenario MarvelCDB has not entered does not exist as far as the
app is concerned. No amount of query fixing changes that.

## Missing from MarvelCDB, so missing from the app

| Pack | Scenarios | Count |
|---|---|---|
| Civil War | Resistance and Registration, for Captain Marvel, Iron Man, Spider-Woman and Captain America | 8 |
| She-Hulk | Resistance: She-Hulk, Registration: She-Hulk | 2 |
| Fear No Evil | The Getaway, Protection Racket, The Raft Breakout, Kingpin | 4 |
| Shadowland | Shadows in the Night, Shadow Labyrinth, Heart of Shadow | 3 |

That is waves 9 to 11, largely. MarvelCDB enters cards as volunteers get to
them, and the newest boxes lag.

## Present in MarvelCDB, verified

Everything else on the list resolves, including both Green Goblin scenarios
(`risky_business`, `mutagen_formula`, pack `gob`). One naming nit: MarvelCDB
spells M.O.D.O.K. without the trailing dot.

## Civil War needs randomiser rules of its own

Worth recording before anybody builds it:

- A Civil War scenario draws **3 to 4 modular sets**, not the usual one or two.
- Its modular sets may only be used with Civil War scenarios or the two She-Hulk
  ones. The randomiser currently treats every owned modular set as drawable by
  every scenario, which would put Hell's Kitchen into Rhino.

## What this means for the pool

The choice is between a pool derived from the card database, which is always
correct about what the app can show cards for and always behind on what exists,
and a curated list, which is complete but cannot name a single card for the
scenarios MarvelCDB lacks.

A curated list would let the randomiser offer "Kingpin" — but the app could not
show its villain deck, its main scheme, or its encounter sets, because it has no
codes for them. Offering a scenario the app then cannot set up may be worse than
not offering it. Undecided, deliberately.
