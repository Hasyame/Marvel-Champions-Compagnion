package com.hasyame.marvelchampions.data.marvelcdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A card exactly as MarvelCDB returns it. Every field the API emits is kept,
 * including ones the app has no use for yet.
 *
 * Field coverage was derived from the full dump of 4375 cards on 2026-08-01.
 * Everything except the handful of always-present fields is nullable, because
 * the API omits null values from top-level card objects rather than emitting
 * them.
 *
 * `linkedCard` is deliberately **not** modelled as a nested card: every linked
 * card also appears as a top-level entry in the same response, so the nested
 * copy is redundant. `linkedToCode` is enough to rebuild the relationship.
 */
@Serializable
data class CardDto(
    // Identity
    val code: String,
    val name: String,
    @SerialName("real_name") val realName: String,
    val position: Int,
    val quantity: Int,
    val url: String? = null,
    @SerialName("octgn_id") val octgnId: String? = null,
    val subname: String? = null,

    // Pack and set
    @SerialName("pack_code") val packCode: String,
    @SerialName("pack_name") val packName: String,
    @SerialName("pack_legacy") val packLegacy: Boolean,
    @SerialName("pack_wave") val packWave: Int? = null,
    @SerialName("card_set_code") val cardSetCode: String? = null,
    @SerialName("card_set_name") val cardSetName: String? = null,
    @SerialName("card_set_type_name_code") val cardSetTypeNameCode: String? = null,
    @SerialName("card_set_parent_code") val cardSetParentCode: String? = null,
    @SerialName("set_position") val setPosition: Int? = null,

    // Classification
    @SerialName("type_code") val typeCode: String,
    @SerialName("type_name") val typeName: String,
    @SerialName("faction_code") val factionCode: String,
    @SerialName("faction_name") val factionName: String,
    val traits: String? = null,
    @SerialName("real_traits") val realTraits: String? = null,

    // Text
    val text: String? = null,
    @SerialName("real_text") val realText: String? = null,
    val flavor: String? = null,
    val errata: String? = null,
    val illustrator: String? = null,
    @SerialName("back_text") val backText: String? = null,
    @SerialName("back_name") val backName: String? = null,
    @SerialName("back_flavor") val backFlavor: String? = null,

    // Images
    val imagesrc: String? = null,
    val backimagesrc: String? = null,

    // Cost and resources
    val cost: Int? = null,
    @SerialName("cost_per_hero") val costPerHero: Boolean = false,
    @SerialName("cost_star") val costStar: Boolean = false,
    @SerialName("resource_physical") val resourcePhysical: Int? = null,
    @SerialName("resource_mental") val resourceMental: Int? = null,
    @SerialName("resource_energy") val resourceEnergy: Int? = null,
    @SerialName("resource_wild") val resourceWild: Int? = null,

    // Hero and ally statistics
    val health: Int? = null,
    @SerialName("health_per_group") val healthPerGroup: Boolean = false,
    @SerialName("health_per_hero") val healthPerHero: Boolean = false,
    @SerialName("health_star") val healthStar: Boolean = false,
    @SerialName("hand_size") val handSize: Int? = null,
    val attack: Int? = null,
    @SerialName("attack_cost") val attackCost: Int? = null,
    @SerialName("attack_star") val attackStar: Boolean = false,
    val thwart: Int? = null,
    @SerialName("thwart_cost") val thwartCost: Int? = null,
    @SerialName("thwart_star") val thwartStar: Boolean = false,
    val defense: Int? = null,
    @SerialName("defense_star") val defenseStar: Boolean = false,
    val recover: Int? = null,
    @SerialName("recover_star") val recoverStar: Boolean = false,

    // Encounter side
    val stage: String? = null,
    val boost: Int? = null,
    @SerialName("boost_star") val boostStar: Boolean = false,
    val scheme: Int? = null,
    @SerialName("scheme_star") val schemeStar: Boolean = false,
    @SerialName("scheme_acceleration") val schemeAcceleration: Int? = null,
    @SerialName("scheme_crisis") val schemeCrisis: Int? = null,
    @SerialName("scheme_hazard") val schemeHazard: Int? = null,
    @SerialName("scheme_amplify") val schemeAmplify: Int? = null,
    val threat: Int? = null,
    @SerialName("threat_fixed") val threatFixed: Boolean = false,
    @SerialName("threat_per_group") val threatPerGroup: Boolean = false,
    @SerialName("threat_star") val threatStar: Boolean = false,
    @SerialName("base_threat") val baseThreat: Int? = null,
    @SerialName("base_threat_fixed") val baseThreatFixed: Boolean = false,
    @SerialName("base_threat_per_group") val baseThreatPerGroup: Boolean = false,
    @SerialName("escalation_threat") val escalationThreat: Int? = null,
    @SerialName("escalation_threat_fixed") val escalationThreatFixed: Boolean = false,
    @SerialName("escalation_threat_star") val escalationThreatStar: Boolean = false,

    // Deck building
    @SerialName("deck_limit") val deckLimit: Int? = null,
    @SerialName("deck_requirements") val deckRequirements: JsonElement? = null,
    @SerialName("deck_options") val deckOptions: JsonElement? = null,
    val restrictions: JsonElement? = null,

    // Relationships
    @SerialName("linked_to_code") val linkedToCode: String? = null,
    @SerialName("linked_to_name") val linkedToName: String? = null,
    @SerialName("duplicate_of_code") val duplicateOfCode: String? = null,
    @SerialName("duplicate_of_name") val duplicateOfName: String? = null,
    @SerialName("duplicated_by") val duplicatedBy: List<String>? = null,

    // Flags and miscellany
    @SerialName("is_unique") val isUnique: Boolean = false,
    val hidden: Boolean = false,
    val permanent: Boolean = false,
    @SerialName("double_sided") val doubleSided: Boolean = false,
    val spoiler: Int? = null,
    val meta: JsonElement? = null,
)
