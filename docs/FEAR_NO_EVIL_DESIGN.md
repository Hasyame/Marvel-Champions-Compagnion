# Fear No Evil — what it needs before a template can be written

Peur de Rien is the first campaign the current schema cannot express. Every
campaign so far has been a fixed chain of scenarios with data carried between
them; this one lets the players choose what to play next, pairs a scenario with
a villain and an environment drawn at the table, and feeds the number of times a
card was *offered* back into setup as a quantity.

This note is the analysis, not the template. Written down because working it out
again from the rulebook would cost more than reading it.

## What already fits

| Mechanic | Existing support |
|---|---|
| Scheme carried forward on its ACHEVÉ / ÉCHOUÉ face | flags + `countTrue`, exactly as Age of Apocalypse carries mission outcomes |
| Unique allies/supports removed from the campaign | `deckCardSelect` prompt into a campaign card list, then a setup step showing it — Galaxy's Most Wanted already does this for The Collection |
| Mary Typhoïde's random face | a `draw` of one from two codes |
| Mary as an ally afterwards, until she is defeated | a flag set by a question, gating a setup step |
| Villain-specific setup text | steps conditional on `drawIs`, as Age of Apocalypse does per mission |

## What does not exist yet

### 1. Players choose the next scenario

`Outcome.next` is a guarded list the engine evaluates — the campaign decides
where to go. Fear No Evil needs a **CHOIX page**: the players pick any scenario
not yet played, and Le Caïd only when nothing else is left.

Needs: a `next` entry meaning "ask", a screen to ask on, and a played-scenarios
list to exclude from. The engine already records `completedScenarios`, so the
exclusion is free once the screen exists.

### 2. The villain is drawn, and brings its own deck

`baseSetup.villainDeck` is a fixed map of difficulty to card codes. Here the
villain is drawn from the five subordinates not yet faced, and *its* stages
become the villain deck — different cards on Expert.

Needs: a villain deck that can be resolved from a draw. Probably a `villainDeck`
entry that names a draw id rather than codes, with the per-villain stage lists
declared once and selected by what came up.

### 3. Choose one of two drawn cards, the other goes back

Draws currently take N and keep them. This needs: offer two, the players pick
one, the chosen card leaves the pool for good, **the other returns to it**.

Needs: a draw that offers rather than decides, and a prompt to record the pick.
`DrawDefinition.count` already offers several; what is missing is the choice and
the asymmetric return to the pool.

### 4. How often a card was offered becomes a number in setup

`$racketEnBandeOrganiséeCOUNT` counts how many times that environment came up in
the CHOIX — offered, not chosen — and that count is the threat each player then
places. L'Évasion du Raft branches on the same count being 1 or more than 1.

Needs: an effect that increments a counter when a card is drawn, and setup steps
that read a counter as a quantity. `showCounter` displays one already; nothing
increments on a draw.

## Scenario notes worth keeping

- **Racket en bande organisée**: one main scheme *per player*, each chosen by
  that player. Nothing in `baseSetup.mainScheme` expresses per-player copies.
- **L'Évasion du Raft**: a branch on a counter — one boost card per PRISONER
  minion when the count is above one.
- Defeat does not end this campaign except at Le Caïd. It feeds the ÉCHOUÉ face
  forward instead, so `onDefeat` needs real effects rather than a replay.

## Suggested order

1. The counter-on-draw effect (small, unlocks Racket and Raft).
2. The choose-one-of-two draw (medium).
3. The villain drawn into the villain deck (medium).
4. The CHOIX screen (largest — new UI, new `next` kind).

Only after all four does the template become a JSON file. Attempting it before
would mean encoding the campaign's rules in prose the app cannot act on, which
is precisely what this schema exists to avoid.

---

# Queued work, decided but not built

## Excluding modular sets from the randomiser

A player who owns part of a pack does not own all of its modular sets, so the
draw offers sets they cannot field. `RandomizerFilters` already excludes
scenarios, heroes and aspects — modular sets are simply missing from it.

The work mirrors the hero exclusion exactly: add `excludedModularSets` to the
filters, `toggleExcludedModularSet` beside `toggleExcludedHero`, apply it in
`ScenarioRandomizer`, and add the chips to the filters card.

**Decided (Benoit, on being asked):** a scenario whose *mandatory* modular sets
include an excluded one is **not offered at all**. If you cannot field a set the
scenario requires, the scenario is not playable, so drawing it and flagging the
gap would only make the player reject it by hand.

## The Rise of Red Skull

Needs no engine work — every mechanic it uses already exists. Card codes are all
present on MarvelCDB under pack `trors`:

- Villains: Crossbones `04058-60`, Absorbing Man `04076-78`, Taskmaster
  `04093-95`, Zola `04109-11`, Red Skull `04125-27`
- Main schemes: `04061a` `04062a` `04063a` · `04079a` · `04096a` · `04112a`
  `04113a` · `04128a` `04129a`
- EXPERIMENTAL attachments: `04072` `04073` `04074` `04075`
- TECH upgrades: `04155` `04156` `04157` `04158`
- Basic Condition upgrades: `04159a` `04160a` `04161a` `04162a`
- Taskmaster allies: `04097` `04098` `04099` `04100`
- Hydra Prison: `04122`

Sets: `crossbones`, `exper_weapon`, `hydra_assault`, `weap_master`,
`hydra_patrol`, `absorbing_man`, `taskmaster`, `zola`, `red_skull`, `expcamp`,
`hydra_camp`.

Shape: a card list for the EXPERIMENTAL attachments the encounter deck keeps
gaining, another for what the players earn and keep, a third for allies lost
behind the Hydra Prison; a campaign counter for the delay markers, which become
threat in the finale; a flag for whether the prison was still standing.

Two corrections to the spec as given: scenario 4 is headed HELA but is Zola
throughout, and the "Legions of Hydra" and "Under Attack" sets it names do not
exist under those codes — the pack ships `hydra_patrol` and `hydra_assault`.
