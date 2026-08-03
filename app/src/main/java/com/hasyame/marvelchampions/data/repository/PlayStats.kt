package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.PlayStatsRow
import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import com.hasyame.marvelchampions.data.db.entity.PlayHero

/** How many games a hero-and-aspect combination needs before it earns a row. */
private const val MIN_PLAYS_FOR_PAIRING = 2

/** Separates a hero from an aspect in a pairing key. */
private const val PAIR = " · "

/**
 * What a play contributes to one table: what it is counted under, and what the
 * player is shown.
 *
 * The two differ because a hero is identified by its card code but read by its
 * name, and the name changes with the interface language. Counting by name
 * would file Spider-Man and L'Araignée as two heroes.
 */
private data class Keyed(val group: String, val label: String)

/**
 * Counting a play history over every seat, rather than only the first.
 *
 * A group game is one game but several heroes, and the two are counted
 * differently on purpose. Totals, streaks and the solo/group split are per
 * game; win rate by hero and by aspect is per seat. A four-player win is one
 * win in the total and a win for each of the four heroes, which is what anyone
 * asking "how does this hero do" means by the question.
 */
internal object PlayStats {

    /**
     * The seats of a play, recovering what can be recovered from an old row.
     *
     * Plays recorded before the roster column existed kept the first hero's
     * code and name, the other heroes' names, and every aspect at the table in
     * one list. That is enough to say who was there, not enough to say who
     * played what — so the aspect is left blank for those and [heroAspects]
     * skips them. An empty row in one table beats a row full of combinations
     * nobody played.
     */
    fun seats(row: PlayStatsRow): List<PlayHero> {
        if (row.roster.isNotEmpty()) {
            return row.roster
        }

        val others = row.otherHeroes.splitList()

        // Solo, and only solo, can be paired with confidence: there is one
        // hero, so the aspects listed are necessarily that hero's.
        if (others.isEmpty()) {
            return listOf(
                PlayHero(
                    code = row.heroCode,
                    name = row.heroName.ifBlank { row.heroCode },
                    aspect = row.aspects.splitList().joinToString(", "),
                ),
            )
        }

        return buildList {
            add(
                PlayHero(
                    code = row.heroCode,
                    name = row.heroName.ifBlank { row.heroCode },
                    aspect = "",
                ),
            )
            // Only names survive for the others; there were never codes for them.
            others.forEach { add(PlayHero(code = "", name = it, aspect = "")) }
        }
    }

    /** Win rate per hero, counting every seat of every game. */
    fun heroes(rows: List<PlayStatsRow>): List<WinRateRow> =
        tally(rows) { row ->
            row.namedSeats().map { Keyed(group = it.groupKey(), label = it.name) }
        }

    /** Win rate per aspect, counting an aspect once per game however many seats brought it. */
    fun aspects(rows: List<PlayStatsRow>): List<WinRateRow> =
        tally(rows) { row ->
            val fromSeats = seats(row).flatMap { it.aspect.splitList() }
            // Old group rows carry their aspects on the play rather than on the
            // seats, and those still belong in the aspect table.
            fromSeats.ifEmpty { row.aspects.splitList() }
                .map { Keyed(group = it, label = it) }
        }

    /**
     * Win rate per hero *with* aspect.
     *
     * Only pairings actually recorded together, and only those played more than
     * once: a table full of one-game hundred-percent rows tells nobody anything
     * and buries the rows that do.
     */
    fun heroAspects(rows: List<PlayStatsRow>): List<WinRateRow> =
        tally(rows, minPlays = MIN_PLAYS_FOR_PAIRING) { row ->
            row.namedSeats().flatMap { seat ->
                seat.aspect.splitList().map {
                    Keyed(group = "${seat.groupKey()}$PAIR$it", label = "${seat.name}$PAIR$it")
                }
            }
        }

    private fun PlayStatsRow.namedSeats(): List<PlayHero> =
        seats(this).filter { it.name.isNotBlank() }

    /** Code where there is one, name otherwise — old rows have no code but the others. */
    private fun PlayHero.groupKey(): String = code.ifBlank { name }

    /**
     * Groups by whatever keys a play yields.
     *
     * Keys are de-duplicated per play, so two seats playing Justice is one
     * Justice game rather than two. Rows arrive newest first, so the label kept
     * for a group is the most recent one it was recorded under — which is the
     * name the player currently sees the hero by.
     */
    private fun tally(
        rows: List<PlayStatsRow>,
        minPlays: Int = 1,
        keysOf: (PlayStatsRow) -> List<Keyed>,
    ): List<WinRateRow> {
        data class Tally(val label: String, var played: Int = 0, var won: Int = 0, var millis: Long = 0)

        val tallies = LinkedHashMap<String, Tally>()
        for (row in rows) {
            for (keyed in keysOf(row).distinctBy { it.group }) {
                val tally = tallies.getOrPut(keyed.group) { Tally(keyed.label) }
                tally.played++
                tally.millis += row.elapsedMillis
                if (row.won) {
                    tally.won++
                }
            }
        }

        return tallies.values
            .asSequence()
            .filter { it.played >= minPlays }
            .map { WinRateRow(it.label, it.played, it.won, it.millis) }
            .sortedWith(compareByDescending<WinRateRow> { it.played }.thenBy { it.key })
            .toList()
    }

    private fun String.splitList(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
