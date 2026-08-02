package com.hasyame.marvelchampions.domain.model

/**
 * Which theme the app uses, as chosen in Settings.
 *
 * [SYSTEM] stays the default: most people set this once at the OS level and
 * expect every app to obey. The override exists because a card app is often
 * read at a table under lighting that has nothing to do with the time of day
 * the system is switching on.
 */
enum class ThemeChoice(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromCode(code: String?): ThemeChoice =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}
