package com.hasyame.marvelchampions.ui.randomizer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.domain.randomizer.DrawField

/** One option a row can be set to: what is stored, and what is read. */
data class DrawOption(val id: String, val label: String)

/**
 * Picks a value for one row of the draw.
 *
 * The randomiser answers "what shall I play", but part of the answer is often
 * already settled — a hero somebody wants to try, a scenario the group agreed
 * on — and only the rest wants rolling. Choosing here locks the row, so the
 * next Roll builds around it instead of throwing it away.
 *
 * [limit] is how many may be picked: one for a scenario or a difficulty,
 * several for heroes and modular sets. When it is one the list behaves as a
 * radio group and closes on the tap, because a confirm button for a single
 * choice is a tap nobody needs.
 */
@Composable
fun ChooseDrawValueDialog(
    title: String,
    options: List<DrawOption>,
    selected: List<String>,
    limit: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    // Kept in pick order: for heroes and aspects that order is the seating,
    // so the first one chosen is the first player.
    //
    // Deliberately unkeyed. Keying on `options` looked tidier but the caller
    // builds that list inline, so every recomposition handed over a new list
    // instance, re-ran the remember, and threw away what had just been ticked —
    // the dialog looked responsive and did nothing.
    val picked = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    var tooMany by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (limit > 1) {
                    Text(
                        text = stringResource(R.string.randomizer_choose_hint, limit),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tooMany) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(options, key = { it.id }) { option ->
                        val isPicked = option.id in picked
                        ListItem(
                            headlineContent = { Text(option.label) },
                            leadingContent = {
                                if (limit == 1) {
                                    RadioButton(selected = isPicked, onClick = null)
                                } else {
                                    Checkbox(checked = isPicked, onCheckedChange = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable {
                                when {
                                    limit == 1 -> onConfirm(listOf(option.id))
                                    isPicked -> {
                                        picked.remove(option.id)
                                        tooMany = false
                                    }
                                    picked.size < limit -> {
                                        picked.add(option.id)
                                        tooMany = false
                                    }
                                    // Silently ignoring the tap reads as a
                                    // broken list, so the hint turns red.
                                    else -> tooMany = true
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (limit > 1) {
                TextButton(
                    onClick = { onConfirm(picked.toList()) },
                    enabled = picked.isNotEmpty(),
                ) { Text(stringResource(R.string.randomizer_choose_confirm)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** How many values a field takes. */
fun DrawField.pickLimit(playerCount: Int): Int = when (this) {
    DrawField.SCENARIO, DrawField.DIFFICULTY, DrawField.PLAYER_COUNT -> 1
    DrawField.HEROES, DrawField.ASPECTS -> playerCount
    // No sensible cap: a scenario can take one modular set or five.
    DrawField.MODULAR_SETS -> Int.MAX_VALUE
}
