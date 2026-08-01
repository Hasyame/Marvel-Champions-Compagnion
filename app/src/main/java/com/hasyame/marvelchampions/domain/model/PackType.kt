package com.hasyame.marvelchampions.domain.model

/**
 * Pack classification. **Not available from the MarvelCDB API** — this comes
 * from the curated `assets/pack_metadata.json`.
 */
enum class PackType {
    /** The Core Set. */
    CORE,

    /** A single hero and their aspect cards. */
    HERO_PACK,

    /** A standalone villain scenario. */
    SCENARIO_PACK,

    /** A large box containing a multi-scenario campaign. */
    CAMPAIGN_BOX,

    /** A standalone modular encounter set, such as Ronan. */
    MODULAR_SET,

    /** A pack whose type could not be determined. Never written by the app. */
    UNKNOWN,
    ;

    companion object {
        fun fromName(name: String): PackType =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}
