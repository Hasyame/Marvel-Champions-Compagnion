package com.hasyame.marvelchampions.domain.model

/**
 * When a finished game gets sent to BoardGameGeek.
 *
 * Off is the default and stays the default. Reporting plays means handing a
 * password to a third party's undocumented endpoint, which nobody should end up
 * doing because they did not read a settings screen carefully.
 */
enum class BggReportingMode(val code: String) {
    /** Never. No credentials are used even if some are stored. */
    OFF("off"),

    /** Offer at the end of each game, and send only if the player says so. */
    ASK("ask"),

    /** Send every finished game without asking. */
    ALWAYS("always"),
    ;

    companion object {
        fun fromCode(code: String?): BggReportingMode =
            entries.firstOrNull { it.code == code } ?: OFF
    }
}

/** One seat at the table, as BoardGameGeek records it. */
data class BggPlayer(
    /** The BGG account, for the player who owns it. Blank for a guest. */
    val username: String,
    val name: String,
    /** Victory points. BGG calls this the score. */
    val score: Int,
    val won: Boolean,
    /** BGG shows this beside the name; the hero played is what belongs there. */
    val color: String,
)

/** A finished game, in the shape BoardGameGeek records one. */
data class BggPlay(
    /** Calendar day the game was played, as `yyyy-MM-dd`. */
    val playedOn: String,
    /** Minutes. BGG stores play length in minutes, not seconds. */
    val lengthMinutes: Int,
    /**
     * The people at the table.
     *
     * A count is not enough: a play posted with only a number lands in BGG
     * with nobody in it, which is what happened. At least the logged-in player
     * has to be here for the play to show up as theirs.
     */
    val players: List<BggPlayer>,
    val won: Boolean,
    val comment: String,
    /** Free text. BGG shows it on the play and groups plays by it. */
    val location: String = "",
)
