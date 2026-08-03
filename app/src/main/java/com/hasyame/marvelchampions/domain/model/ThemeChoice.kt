package com.hasyame.marvelchampions.domain.model

/**
 * Which theme the app uses, as chosen in Settings.
 *
 * [DARK] is the default. Red on near-black is the palette this game is drawn
 * in, and it is what a table lit for playing wants — following the system
 * would put half the players in a bright white app in a dim room. The choice
 * stays, including following the system for anyone who prefers that.
 */
enum class ThemeChoice(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromCode(code: String?): ThemeChoice =
            entries.firstOrNull { it.code == code } ?: DARK
    }
}
