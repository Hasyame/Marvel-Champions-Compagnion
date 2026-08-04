package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.hasyame.marvelchampions.data.repository.CampaignRun

/**
 * Words a campaign gives a specific meaning to, which the printed material sets
 * apart from ordinary prose.
 *
 * MISSION and OVERSEER are not descriptions in Age of Apocalypse: a MISSION side
 * scheme cannot be thwarted and an OVERSEER minion sits outside any player's
 * control. Reading them as ordinary words loses that, so they are set in bold
 * italic the way the campaign sets them.
 */
private val KEYWORDS = listOf("MISSION", "OVERSEER", "PRELATE")

private val KEYWORD_PATTERN = Regex(KEYWORDS.joinToString("|") { "\\b$it\\b" })

private val KEYWORD_STYLE = SpanStyle(
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
)

/** Marks up the campaign's own keywords wherever they appear in [text]. */
fun campaignText(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in KEYWORD_PATTERN.findAll(text)) {
        append(text.substring(index, match.range.first))
        withStyleSpan(match.value)
        index = match.range.last + 1
    }
    append(text.substring(index))
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleSpan(value: String) {
    pushStyle(KEYWORD_STYLE)
    append(value)
    pop()
}

/**
 * Fills `{drawId}` in a template string with the card the app drew.
 *
 * A question like "was the MISSION defeated?" is answerable but vague — there
 * are five of them and the app chose one. Naming it means the player is
 * confirming the thing actually on their table rather than translating from a
 * generic question.
 */
fun resolveDraws(text: String, run: CampaignRun, scenarioId: String?): String =
    Regex("""\{([A-Za-z0-9_]+)}""").replace(text) { match ->
        val drawn = run.state.draws[scenarioId].orEmpty()[match.groupValues[1]].orEmpty()
        if (drawn.isEmpty()) {
            // Nothing drawn: drop the placeholder rather than print braces.
            ""
        } else {
            drawn.joinToString(", ") { run.names.card(it) }
        }
    }.replace("  ", " ").trim()
