package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.repository.CampaignDeckCard
import com.hasyame.marvelchampions.data.repository.CampaignRun
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.PromptType
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate

/**
 * Page 3. The post-victory questionnaire, entirely driven by the template.
 *
 * Prompts whose `when` fails are not shown, which is how the Expert-only
 * questions disappear on Standard without this file knowing anything about
 * difficulty.
 */
@Composable
fun QuestionsPage(
    run: CampaignRun,
    scenario: ScenarioTemplate?,
    onSubmit: (AnswerSet) -> Unit,
) {
    val context = EvaluationContext(state = run.state, scenarioId = scenario?.id)
    val prompts = scenario?.onVictory?.prompts.orEmpty()
        .filter { ConditionEvaluator.evaluate(it.condition, context) }

    val numbers = remember { mutableStateMapOf<String, String>() }
    val choices = remember { mutableStateMapOf<String, String>() }
    val cardLists = remember { mutableStateMapOf<String, String>() }
    val cardSelections = remember { mutableStateMapOf<String, Set<String>>() }
    val perHeroNumbers = remember { mutableStateMapOf<String, String>() }
    val perHeroBooleans = remember { mutableStateMapOf<String, Boolean>() }

    // Every switch on the page starts recorded as "no". A map that only gains a
    // key when a switch is touched cannot tell "answered no" from "not asked",
    // and the log is meant to be a record of what the player said.
    val booleans = remember(prompts) {
        mutableStateMapOf<String, Boolean>().apply {
            prompts.filter { it.promptType == PromptType.BOOLEAN }
                .forEach { put(it.id, false) }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.campaign_questions_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (prompts.isEmpty()) {
            // Silence here used to be indistinguishable from a broken screen,
            // so it now says which of the three reasons applies.
            Text(
                text = when {
                    scenario == null -> stringResource(R.string.campaign_no_scenario)
                    scenario.onVictory == null ->
                        stringResource(R.string.campaign_scenario_incomplete, scenario.id)

                    scenario.onVictory.prompts.isEmpty() ->
                        stringResource(R.string.campaign_scenario_incomplete, scenario.id)

                    else -> stringResource(R.string.campaign_no_questions)
                },
                color = MaterialTheme.colorScheme.error,
            )
        }

        prompts.forEach { prompt ->
            val label = prompt.label?.resolve("fr").orEmpty().ifBlank { prompt.id }
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (prompt.promptType) {
                        PromptType.NUMBER -> OutlinedTextField(
                            value = numbers[prompt.id].orEmpty(),
                            onValueChange = { numbers[prompt.id] = it.filter(Char::isDigit) },
                            label = { Text(label) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PromptType.BOOLEAN -> Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, Modifier.weight(1f))
                            Switch(
                                checked = booleans[prompt.id] ?: false,
                                onCheckedChange = { booleans[prompt.id] = it },
                            )
                        }

                        PromptType.PER_HERO_NUMBER -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            run.state.heroes.forEach { hero ->
                                val key = "${prompt.id}|${hero.id}"
                                OutlinedTextField(
                                    value = perHeroNumbers[key].orEmpty(),
                                    onValueChange = {
                                        perHeroNumbers[key] = it.filter(Char::isDigit)
                                    },
                                    label = { Text(hero.name) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        PromptType.PER_HERO_BOOLEAN -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            run.state.heroes.forEach { hero ->
                                val key = "${prompt.id}|${hero.id}"
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(hero.name, Modifier.weight(1f))
                                    Switch(
                                        checked = perHeroBooleans[key] ?: false,
                                        onCheckedChange = { perHeroBooleans[key] = it },
                                    )
                                }
                            }
                        }

                        PromptType.CARD_SELECT -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            // Codes are recorded, names are shown, so a later
                            // scenario can act on the answer rather than only
                            // repeat it back.
                            prompt.cards.forEach { code ->
                                val selected = cardSelections[prompt.id].orEmpty().contains(code)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(run.names.card(code), Modifier.weight(1f))
                                    Switch(
                                        checked = selected,
                                        onCheckedChange = { on ->
                                            val current = cardSelections[prompt.id].orEmpty()
                                            cardSelections[prompt.id] = if (on) {
                                                current + code
                                            } else {
                                                current - code
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        PromptType.DECK_CARD_SELECT -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            DeckCardPicker(
                                deckCards = run.deckCards,
                                selected = cardSelections[prompt.id].orEmpty(),
                                onToggle = { code, on ->
                                    val current = cardSelections[prompt.id].orEmpty()
                                    cardSelections[prompt.id] =
                                        if (on) current + code else current - code
                                },
                            )
                        }

                        PromptType.CARD_LIST -> OutlinedTextField(
                            value = cardLists[prompt.id].orEmpty(),
                            onValueChange = { cardLists[prompt.id] = it },
                            label = { Text(label) },
                            supportingText = {
                                Text(stringResource(R.string.campaign_card_list_hint))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        PromptType.CHOICE -> {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                prompt.options.forEach { option ->
                                    FilterChip(
                                        selected = choices[prompt.id] == option.id,
                                        onClick = { choices[prompt.id] = option.id },
                                        label = {
                                            Text(
                                                option.label?.resolve("fr").orEmpty()
                                                    .ifBlank { option.id },
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        PromptType.UNKNOWN -> Text(
                            text = stringResource(R.string.campaign_unknown_prompt, prompt.type),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                onSubmit(
                    AnswerSet(
                        numbers = numbers.mapNotNull { (k, v) ->
                            v.toIntOrNull()?.let { k to it }
                        }.toMap(),
                        booleans = booleans.toMap(),
                        choices = choices.toMap(),
                        cardLists = cardLists.mapValues { (_, v) ->
                            v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        } + cardSelections.mapValues { (_, codes) -> codes.toList() },
                        perHeroNumbers = perHeroNumbers.entries
                            .mapNotNull { (key, value) ->
                                val parts = key.split('|')
                                val number = value.toIntOrNull()
                                if (parts.size == 2 && number != null) {
                                    Triple(parts[0], parts[1], number)
                                } else {
                                    null
                                }
                            }
                            .groupBy({ it.first }, { it.second to it.third })
                            .mapValues { entry -> entry.value.toMap() },
                        perHeroBooleans = perHeroBooleans.entries
                            .mapNotNull { (key, value) ->
                                val parts = key.split('|')
                                if (parts.size == 2) Triple(parts[0], parts[1], value) else null
                            }
                            .groupBy({ it.first }, { it.second to it.third })
                            .mapValues { entry -> entry.value.toMap() },
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.campaign_validate)) }
    }
}

/**
 * Picks cards out of the decks in play, grouped by the player who owns them.
 *
 * Typing card titles was the alternative, and it fails in both directions: a
 * misspelling records something no later scenario can match, and a player with
 * two identical titles across decks cannot say whose copy it was. Grouping by
 * hero answers that, and the filter keeps a fifty-card deck usable on a phone.
 */
@Composable
private fun DeckCardPicker(
    deckCards: List<CampaignDeckCard>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    if (deckCards.isEmpty()) {
        Text(
            text = stringResource(R.string.campaign_no_deck_cards),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    var filter by remember { mutableStateOf("") }

    OutlinedTextField(
        value = filter,
        onValueChange = { filter = it },
        label = { Text(stringResource(R.string.campaign_filter_cards)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    val matching = deckCards.filter { it.cardName.contains(filter, ignoreCase = true) }

    // Selected cards stay visible even when the filter would hide them, so a
    // choice cannot be silently lost behind a search term.
    val visible = (matching + deckCards.filter { it.cardCode in selected }).distinct()

    visible.groupBy { it.heroId }.forEach { (_, cards) ->
        Text(
            text = cards.first().heroName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        cards.sortedBy { it.cardName }.forEach { entry ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.cardName)
                    entry.typeName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = entry.cardCode in selected,
                    onCheckedChange = { on -> onToggle(entry.cardCode, on) },
                )
            }
        }
    }
}
