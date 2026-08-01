package com.hasyame.marvelchampions.domain.deckbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckValidatorTest {

    private val heroRules = HeroDeckRules(
        heroCode = "01001a",
        heroSetCode = "spider_man",
        aspectCount = 1,
    )

    private fun card(
        code: String,
        faction: String,
        type: String = "ally",
        setCode: String? = null,
        deckLimit: Int? = 3,
        unique: Boolean = false,
        traits: String? = null,
        energy: Int? = null,
    ) = DeckCardInfo(
        code = code,
        name = "Card $code",
        factionCode = faction,
        typeCode = type,
        cardSetCode = setCode,
        traits = traits,
        deckLimit = deckLimit,
        isUnique = unique,
        resourceEnergy = energy,
    )

    /** A legal filler deck of the required size, all from one aspect. */
    private fun fillerDeck(count: Int = MINIMUM_DECK_SIZE, faction: String = "justice") =
        (1..count).associate { "j$it" to 1 } to
            (1..count).associate { "j$it" to card("j$it", faction) }

    @Test
    fun `a deck of the minimum size in one aspect is legal`() {
        val (slots, cards) = fillerDeck()

        val result = DeckValidator.validate(heroRules, listOf("justice"), slots, cards)

        assertTrue(result.problems.toString(), result.isLegal)
        assertEquals(MINIMUM_DECK_SIZE, result.totalCards)
    }

    @Test
    fun `too few cards is reported with both numbers`() {
        val (slots, cards) = fillerDeck(count = 10)

        val result = DeckValidator.validate(heroRules, listOf("justice"), slots, cards)

        assertTrue(
            result.problems.any { it == DeckProblem.TooFewCards(10, MINIMUM_DECK_SIZE) },
        )
    }

    @Test
    fun `a card from another aspect is rejected`() {
        val (slots, cards) = fillerDeck()
        val withIntruder = slots + ("agg1" to 1)
        val allCards = cards + ("agg1" to card("agg1", "aggression"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withIntruder, allCards)

        assertTrue(
            result.problems.any {
                it is DeckProblem.OffAspectCard && it.cardCode == "agg1"
            },
        )
    }

    @Test
    fun `basic cards are always allowed`() {
        val (slots, cards) = fillerDeck()
        val withBasic = slots + ("b1" to 1)
        val allCards = cards + ("b1" to card("b1", "basic"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withBasic, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `the hero's own cards are allowed`() {
        val (slots, cards) = fillerDeck()
        val withSignature = slots + ("h1" to 1)
        val allCards = cards + ("h1" to card("h1", "hero", setCode = "spider_man"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withSignature, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `another hero's signature card is rejected`() {
        // Faction alone is not enough: hero-faction cards belong to one hero.
        val (slots, cards) = fillerDeck()
        val withForeign = slots + ("x1" to 1)
        val allCards = cards + ("x1" to card("x1", "hero", setCode = "iron_man"))

        val result = DeckValidator.validate(heroRules, listOf("justice"), withForeign, allCards)

        assertTrue(
            result.problems.any {
                it is DeckProblem.OffAspectCard && it.cardCode == "x1"
            },
        )
    }

    @Test
    fun `exceeding the copy limit is reported`() {
        val (slots, cards) = fillerDeck(count = MINIMUM_DECK_SIZE)
        val over = slots + ("j1" to 4)

        val result = DeckValidator.validate(heroRules, listOf("justice"), over, cards)

        assertTrue(
            result.problems.any {
                it is DeckProblem.OverCopyLimit && it.cardCode == "j1" && it.limit == 3
            },
        )
    }

    @Test
    fun `a duplicated unique card is reported`() {
        val (slots, cards) = fillerDeck()
        val withUnique = slots + ("u1" to 2)
        val allCards = cards + (
            "u1" to card("u1", "justice", deckLimit = 1, unique = true)
            )

        val result = DeckValidator.validate(heroRules, listOf("justice"), withUnique, allCards)

        assertTrue(result.problems.any { it is DeckProblem.DuplicateUniqueCard })
    }

    @Test
    fun `the wrong number of aspects is reported`() {
        val (slots, cards) = fillerDeck()

        val result = DeckValidator.validate(heroRules, listOf("justice", "aggression"), slots, cards)

        assertTrue(result.problems.any { it == DeckProblem.WrongAspectCount(2, 1) })
    }

    @Test
    fun `spider-woman needs exactly two aspects`() {
        // The only two heroes with deck_requirements in the data are
        // Spider-Woman (2 aspects) and Adam Warlock (4, one card each).
        val spiderWoman = heroRules.copy(heroSetCode = "spider_woman", aspectCount = 2)
        val (slots, cards) = fillerDeck()

        assertFalse(
            DeckValidator.validate(spiderWoman, listOf("justice"), slots, cards).isLegal,
        )
        assertTrue(
            DeckValidator.validate(
                spiderWoman,
                listOf("justice", "aggression"),
                slots,
                cards,
            ).problems.none { it is DeckProblem.WrongAspectCount },
        )
    }

    @Test
    fun `adam warlock may take only one card per aspect`() {
        val warlock = HeroDeckRules(
            heroCode = "21031a",
            heroSetCode = "adam_warlock",
            aspectCount = 4,
            perAspectLimit = 1,
        )
        val slots = mapOf("j1" to 1, "j2" to 1)
        val cards = mapOf(
            "j1" to card("j1", "justice"),
            "j2" to card("j2", "justice"),
        )

        val result = DeckValidator.validate(
            warlock,
            listOf("justice", "aggression", "leadership", "protection"),
            slots,
            cards,
        )

        assertTrue(
            result.problems.any {
                it is DeckProblem.OverAspectLimit && it.aspect == "justice" && it.limit == 1
            },
        )
    }

    @Test
    fun `a deck option admits an otherwise off-aspect card`() {
        // Cyclops: {"trait":["x-men"],"type":["ally"]} — X-Men allies of any
        // aspect are legal for him.
        val cyclops = heroRules.copy(
            heroSetCode = "cyclops",
            options = listOf(DeckOption(traits = listOf("x-men"), types = listOf("ally"))),
        )
        val (slots, cards) = fillerDeck()
        val withXMen = slots + ("x1" to 1)
        val allCards = cards + (
            "x1" to card("x1", "aggression", type = "ally", traits = "X-Men.")
            )

        val result = DeckValidator.validate(cyclops, listOf("justice"), withXMen, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `a deck option does not admit a card that fails one of its criteria`() {
        val cyclops = heroRules.copy(
            heroSetCode = "cyclops",
            options = listOf(DeckOption(traits = listOf("x-men"), types = listOf("ally"))),
        )
        val (slots, cards) = fillerDeck()
        // Right trait, wrong type.
        val withEvent = slots + ("x1" to 1)
        val allCards = cards + (
            "x1" to card("x1", "aggression", type = "event", traits = "X-Men.")
            )

        val result = DeckValidator.validate(cyclops, listOf("justice"), withEvent, allCards)

        assertTrue(result.problems.any { it is DeckProblem.OffAspectCard })
    }

    @Test
    fun `a limited deck option stops admitting once it is full`() {
        // Gamora: {"limit":6,"trait":["attack","thwart"],"type":["event"]}
        val gamora = heroRules.copy(
            heroSetCode = "gamora",
            options = listOf(
                DeckOption(traits = listOf("attack", "thwart"), types = listOf("event"), limit = 6),
            ),
        )
        val (slots, cards) = fillerDeck()
        val withSeven = slots + ("g1" to 7)
        val allCards = cards + (
            "g1" to card("g1", "aggression", type = "event", traits = "Attack.", deckLimit = 9)
            )

        val result = DeckValidator.validate(gamora, listOf("justice"), withSeven, allCards)

        assertTrue(result.problems.any { it is DeckProblem.OffAspectCard })
    }

    @Test
    fun `a resource based deck option matches on the printed resource`() {
        // Wonder Man: {"resource":["energy"],"type":["event"]}
        val wonderMan = heroRules.copy(
            heroSetCode = "wonder_man",
            options = listOf(DeckOption(resources = listOf("energy"), types = listOf("event"))),
        )
        val (slots, cards) = fillerDeck()
        val withEnergyEvent = slots + ("w1" to 1)
        val allCards = cards + (
            "w1" to card("w1", "aggression", type = "event", energy = 1)
            )

        val result = DeckValidator.validate(wonderMan, listOf("justice"), withEnergyEvent, allCards)

        assertTrue(result.problems.toString(), result.isLegal)
    }

    @Test
    fun `a card missing from the database is skipped rather than rejected`() {
        // Happens when a deck references a pack newer than the last card sync.
        val (slots, cards) = fillerDeck()
        val withUnknown = slots + ("unknown" to 2)

        val result = DeckValidator.validate(heroRules, listOf("justice"), withUnknown, cards)

        assertTrue(result.problems.none { it is DeckProblem.OffAspectCard })
    }

    @Test
    fun `an empty deck reports too few cards and nothing else`() {
        val result = DeckValidator.validate(heroRules, listOf("justice"), emptyMap(), emptyMap())

        assertEquals(1, result.problems.size)
        assertTrue(result.problems.single() is DeckProblem.TooFewCards)
    }
}
