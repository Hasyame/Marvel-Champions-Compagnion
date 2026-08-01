package com.hasyame.marvelchampions.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero
import com.hasyame.marvelchampions.domain.campaign.engine.CampaignState
import com.hasyame.marvelchampions.domain.campaign.engine.ConditionEvaluator
import com.hasyame.marvelchampions.domain.campaign.engine.EvaluationContext
import com.hasyame.marvelchampions.domain.campaign.template.PromptType
import com.hasyame.marvelchampions.domain.campaign.template.ScenarioTemplate

/**
 * The post-scenario questionnaire.
 *
 * Entirely driven by the template's prompt list — this file knows nothing about
 * any particular campaign. Prompts whose `when` fails are not shown, which is
 * how Expert-only questions disappear on Standard.
 */
@Composable
fun ScenarioOutcomeDialog(
    scenario: ScenarioTemplate?,
    victory: Boolean,
    heroes: List<CampaignHero>,
    difficulty: String,
    state: CampaignState,
    onDismiss: () -> Unit,
    onConfirm: (AnswerSet) -> Unit,
) {
    val outcome = if (victory) scenario?.onVictory else scenario?.onDefeat
    val context = EvaluationContext(state = state, scenarioId = scenario?.id)
    val prompts = outcome?.prompts.orEmpty()
        .filter { ConditionEvaluator.evaluate(it.condition, context) }

    val numbers = remember { mutableStateMapOf<String, String>() }
    val booleans = remember { mutableStateMapOf<String, Boolean>() }
    val choices = remember { mutableStateMapOf<String, String>() }
    val cardLists = remember { mutableStateMapOf<String, String>() }
    val perHeroNumbers = remember { mutableStateMapOf<String, String>() }
    val perHeroBooleans = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (victory) R.string.campaign_victory else R.string.campaign_defeat,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (prompts.isEmpty()) {
                    Text(stringResource(R.string.campaign_no_questions))
                }
                prompts.forEach { prompt ->
                    val label = prompt.label?.resolve("fr").orEmpty().ifBlank { prompt.id }
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
                            heroes.forEach { hero ->
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
                            heroes.forEach { hero ->
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AnswerSet(
                            numbers = numbers.mapNotNull { (k, v) ->
                                v.toIntOrNull()?.let { k to it }
                            }.toMap(),
                            booleans = booleans.toMap(),
                            choices = choices.toMap(),
                            cardLists = cardLists.mapValues { (_, v) ->
                                v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            },
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
            ) { Text(stringResource(R.string.campaign_record)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
