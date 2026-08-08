# Field report, 6 August 2026

Four bugs from two real games, at two and three players. Written down with a
first hypothesis each, because these came from play and would be expensive to
reproduce from a description alone.

## 1. A scenario shows the wrong French name

Picking the Green Goblin scenario shows **"Le Bouffon Venom"**, which is not a
thing. Seen in the randomiser and in scenario selection.

**Hypothesis.** `CardDao.getPlayableScenarios` and `getCardSets` both group by
`cardSetCode` and take `MIN(cardSetName)`. `MIN` on a string is alphabetical, so
if MarvelCDB's French rows disagree about the set's name — even for one card —
the query silently picks whichever sorts first rather than the right one. That
would produce exactly this: a real name, from the right pack, on the wrong
scenario.

Check first: `SELECT DISTINCT cardSetCode, cardSetName FROM cards WHERE locale =
'fr' AND cardSetCode IN ('mutagen_formula','risky_business')`. If a code has more
than one name, that is the bug, and the fix is to pick the most frequent name
rather than the smallest.

## 2. The modular set stops rerolling

After tapping Roll a few times the modular set field sticks and will not change
again.

**Hypothesis.** `mandatoryModularCodes` is merged into the draw on every roll. If
a mandatory set from a previous scenario is never cleared when the scenario
changes, the field would converge on a fixed value and stop moving. Worth
checking whether `DrawField.MODULAR_SETS` is being silently added to `locked`.

## 3. The randomiser ignores the collection

Offers modular sets, heroes and difficulties the player does not have.

**Hypothesis, and this one is close to certain.** `RandomizerViewModel` calls
`repository.loadPools(locale)` exactly once, in `init`. The view model outlives a
trip to Settings, so changing the collection and coming back leaves the old pools
in place — the same bug that was fixed for the deck list on the campaign start
screen, in the same shape, one screen over. `loadPools` filters by `packCode in
owned`, so it is correct; it is just answering a question asked before the user
changed the answer.

Fix: collect `collectionRepository.observeOwnedCodes()` and rebuild the pools
when it changes, exactly as `observeExcludedModularSets()` already does.

Difficulty is not collection-derived, so "difficulty I do not have" is a separate
thing and needs clarifying with Benoit — most likely the allowed-difficulty
filter is not being applied to the draw.

## 4. The BoardGameGeek payload is thin

What is sent needs:

- **Player count.** Two or three heroes at the table is a two- or three-player
  game; it currently always reports one.
- **The real date.** The play should be filed under the day it happened.
- **Start and end times**, to the minute. The app already times the game, so both
  are known — they are simply not being sent.
