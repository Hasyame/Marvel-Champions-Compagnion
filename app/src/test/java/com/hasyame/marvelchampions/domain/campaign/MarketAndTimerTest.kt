package com.hasyame.marvelchampions.domain.campaign

import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEngine
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.MarketRules
import com.hasyame.marvelchampions.domain.campaign.engine.PurchaseRefusal
import com.hasyame.marvelchampions.domain.campaign.engine.TimerState
import com.hasyame.marvelchampions.domain.campaign.template.CampaignTemplate
import com.hasyame.marvelchampions.domain.campaign.template.CardListDefinition
import com.hasyame.marvelchampions.domain.campaign.template.CounterDefinition
import com.hasyame.marvelchampions.domain.campaign.template.LocalizedText
import com.hasyame.marvelchampions.domain.campaign.template.MarketDefinition
import com.hasyame.marvelchampions.domain.campaign.template.MarketEntry
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRulesTest {

    private val engine = CampaignEngine()

    private val template = CampaignTemplate(
        id = "t",
        schemaVersion = 1,
        name = LocalizedText(fr = "T"),
        counters = listOf(CounterDefinition(id = "credits", scope = "hero", initial = 0, min = 0)),
        cardLists = listOf(CardListDefinition(id = "purchases", scope = "hero")),
        market = MarketDefinition(
            counterId = "credits",
            entries = listOf(
                MarketEntry(cardCode = "m1", cost = 2),
                MarketEntry(cardCode = "m2", cost = 5),
            ),
        ),
        scenarios = listOf(ScenarioTemplate(id = "s1")),
    )

    private val heroes = listOf(
        CampaignHero("h1", null, "a", "One"),
        CampaignHero("h2", null, "b", "Two"),
    )

    private fun stateWithCredits(h1: Int, h2: Int, extra: List<CampaignEvent> = emptyList()) =
        engine.fold(
            template,
            listOf(
                CampaignEvent.CampaignStarted("e0", 1, "t", "standard", heroes, "s1"),
                CampaignEvent.ManualAdjustment("e1", 2, counterId = "credits", heroId = "h1", value = h1),
                CampaignEvent.ManualAdjustment("e2", 3, counterId = "credits", heroId = "h2", value = h2),
            ) + extra,
        )

    @Test
    fun `a hero cannot buy a card costing more than their own credits`() {
        val state = stateWithCredits(h1 = 1, h2 = 9)

        assertEquals(
            PurchaseRefusal.NotEnoughCredits,
            MarketRules.canPurchase(template, state, "h1", "m1"),
        )
        // Credits are per hero, so the other player being rich changes nothing.
        assertNull(MarketRules.canPurchase(template, state, "h2", "m1"))
    }

    @Test
    fun `one copy of each market card per campaign across the whole group`() {
        val bought = CampaignEvent.MarketPurchase(
            id = "e3", timestamp = 4, heroId = "h1",
            cardCode = "m1", cost = 2, cardListId = "purchases",
        )
        val state = stateWithCredits(h1 = 9, h2 = 9, extra = listOf(bought))

        // The other hero can afford it, but the group already has it.
        assertEquals(
            PurchaseRefusal.AlreadyOwnedByGroup("h1"),
            MarketRules.canPurchase(template, state, "h2", "m1"),
        )
    }

    @Test
    fun `a refunded card becomes buyable again`() {
        val bought = CampaignEvent.MarketPurchase(
            id = "e3", timestamp = 4, heroId = "h1",
            cardCode = "m1", cost = 2, cardListId = "purchases",
        )
        val refund = CampaignEvent.MarketRefund("e4", 5, purchaseEventId = "e3")
        val state = stateWithCredits(h1 = 9, h2 = 9, extra = listOf(bought, refund))

        assertNull(MarketRules.canPurchase(template, state, "h2", "m1"))
    }

    @Test
    fun `offers report why each card cannot be bought`() {
        val bought = CampaignEvent.MarketPurchase(
            id = "e3", timestamp = 4, heroId = "h2",
            cardCode = "m1", cost = 2, cardListId = "purchases",
        )
        val state = stateWithCredits(h1 = 3, h2 = 9, extra = listOf(bought))

        val offers = MarketRules.offersFor(template, state, "h1").associateBy { it.entry.cardCode }

        assertEquals(PurchaseRefusal.AlreadyOwnedByGroup("h2"), offers.getValue("m1").refusal)
        assertEquals(PurchaseRefusal.NotEnoughCredits, offers.getValue("m2").refusal)
    }

    @Test
    fun `a purchased card joins that hero's list only`() {
        val bought = CampaignEvent.MarketPurchase(
            id = "e3", timestamp = 4, heroId = "h1",
            cardCode = "m1", cost = 2, cardListId = "purchases",
        )
        val state = stateWithCredits(h1 = 9, h2 = 9, extra = listOf(bought))

        assertEquals(listOf("m1"), state.heroCards("purchases", "h1"))
        assertTrue(state.heroCards("purchases", "h2").isEmpty())
    }
}

class ScenarioTimerTest {

    @Test
    fun `a paused timer reports the banked time`() {
        val timer = TimerState(accumulatedMillis = 5_000)

        assertEquals(5_000, timer.elapsedAt(nowEpochMillis = 999_999))
        assertFalse(timer.isRunning)
    }

    @Test
    fun `a running timer keeps counting`() {
        val timer = TimerState().start(nowEpochMillis = 1_000)

        assertEquals(4_000, timer.elapsedAt(nowEpochMillis = 5_000))
    }

    @Test
    fun `pausing banks the elapsed time`() {
        val paused = TimerState().start(1_000).pause(4_000)

        assertEquals(3_000, paused.accumulatedMillis)
        assertEquals(3_000, paused.elapsedAt(nowEpochMillis = 900_000))
    }

    @Test
    fun `resuming adds to what was already banked`() {
        val timer = TimerState().start(1_000).pause(4_000).start(10_000)

        assertEquals(5_000, timer.elapsedAt(nowEpochMillis = 12_000))
    }

    @Test
    fun `elapsed time survives a reboot because it is wall-clock based`() {
        // SystemClock.elapsedRealtime would have reset here; a stored wall-clock
        // instant does not.
        val beforeReboot = TimerState().start(nowEpochMillis = 1_000_000)

        assertEquals(600_000, beforeReboot.elapsedAt(nowEpochMillis = 1_600_000))
    }

    @Test
    fun `a backwards clock change never reduces elapsed time`() {
        val timer = TimerState(accumulatedMillis = 5_000, runningSinceEpochMillis = 10_000)

        assertEquals(5_000, timer.elapsedAt(nowEpochMillis = 8_000))
    }

    @Test
    fun `elapsed time can be corrected by hand while running`() {
        val corrected = TimerState().start(1_000).setElapsed(60_000, nowEpochMillis = 5_000)

        assertTrue(corrected.isRunning)
        assertEquals(62_000, corrected.elapsedAt(nowEpochMillis = 7_000))
    }

    @Test
    fun `a negative correction is clamped to zero`() {
        assertEquals(0, TimerState().setElapsed(-5, 1_000).accumulatedMillis)
    }

    @Test
    fun `starting an already running timer does not restart it`() {
        val timer = TimerState().start(1_000)

        assertEquals(timer, timer.start(9_000))
    }

    @Test
    fun `formatting shows hours only when needed`() {
        assertEquals("1:05", TimerState.format(65_000))
        assertEquals("1:01:05", TimerState.format(3_665_000))
        assertEquals("0:00", TimerState.format(0))
    }
}
