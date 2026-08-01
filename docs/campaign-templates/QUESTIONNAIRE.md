# Campaign questionnaire

What I need from you to turn `TEMPLATE_BLANK.json` into a working Galaxy's Most
Wanted campaign.

**I have deliberately not filled any of this in.** Scenario text, credit rules
and setup instructions are campaign book content — reconstructing them from
memory would produce plausible-looking numbers that are quietly wrong, which is
worse than blanks.

Answer in French; English can be added later and falls back to French when
missing.

---

## A. Once for the whole campaign

1. **Scenario order.** The scenario ids in order, and which one starts the
   campaign. Anything that branches — say so and I will model it as a condition.
2. **Counters.** I have assumed three: `credits` (per hero), `vp` (cumulative,
   campaign-wide) and `hp` (per hero, Expert only, capped at printed health).
   - Is `vp` really cumulative across the campaign, or per scenario only?
   - Anything else the campaign tracks?
3. **Flags.** Which yes/no facts carry between scenarios? For each: what sets it,
   and which later scenario reads it. If a later scenario counts *how many* are
   set, say so — that is the `countTrue` case.
4. **The market.**
   - Every buyable card: its MarvelCDB code and its credit cost.
   - Is it available after every scenario, or only some?
   - Can a hero buy more than one card at a time?
   - Confirm: one copy of each card per campaign **across the whole group**, not
     per hero. That is what the brief said and what I implemented.

## B. For each scenario

Copy this block once per scenario.

```
Scenario id:
Name (fr):

1. FLAVOUR TEXT
   The text read at the start.

2. BASE SETUP
   - Villain stages on Standard:
   - Villain stages on Expert:
   - Main scheme cards, in order:
   - Encounter sets to shuffle together:
   - Modular sets:

3. CAMPAIGN SETUP
   Numbered instructions, in order. For each, say whether it always applies or
   is conditional on:
     - difficulty
     - a flag from an earlier scenario
     - a counter value
   Mark any step that is an ACTION the player may take rather than text to
   read — what it costs, and what it changes.

4. VICTORY QUESTIONNAIRE
   What should the app ask after a win? For each question:
     - the wording
     - the type: number / yes-no / number per hero / yes-no per hero /
       list of cards / choice from a list
     - whether it only appears on Expert

5. VICTORY EFFECTS
   What each answer does, as separate steps. For example:
     "1 credit per hero, plus 1 per victory point up to a maximum of 3,
      plus 1 more if the scheme was at 1B."
   Say it in words; I will translate it into steps.

6. DEFEAT
   Anything recorded on a loss? Does a loss change any counter or flag?

7. WHAT COMES NEXT
   After a win: which scenario, and under what condition if it branches.
   After a loss: replay this one, or something else?
```

## C. Answered

1. **Deck size.** Minimum 40, maximum 50, unless a hero has its own rule.
   Implemented. No hero in the card data states a deck size, so per-hero
   exceptions live in the curated `HERO_DECK_SIZE_OVERRIDES`, currently empty.
2. **Campaign-only scenarios.** None are excluded — a scenario is drawable if
   its pack is owned, which is already how the randomiser works. No change
   needed.
3. **Hit points between scenarios.** Expert only, unless a campaign says
   otherwise. Already the case: carrying hit points is expressed in the template
   as `activeWhen: { "difficulty": "expert" }` on the counter and
   `when: { "difficulty": "expert" }` on the effect. A campaign that wants them
   on Standard simply omits the guard — the engine hardcodes nothing.

## D. Still open

1. **Whether victory points are spent or only accumulated.**

## E. What happens next

Once you send section B for scenario 1, I will fill it in, import it, and show
you the result before you write the rest — so a misunderstanding costs one
scenario rather than five.
