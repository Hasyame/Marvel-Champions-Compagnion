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

/** A finished game, in the shape BoardGameGeek records one. */
data class BggPlay(
    /** Calendar day the game was played, as `yyyy-MM-dd`. */
    val playedOn: String,
    /** Minutes. BGG stores play length in minutes, not seconds. */
    val lengthMinutes: Int,
    val players: Int,
    /** BGG has no win flag on the play itself, so the outcome goes in the comment. */
    val won: Boolean,
    val comment: String,
)
