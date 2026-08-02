package com.hasyame.marvelchampions.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.core.designsystem.component.comicTopBarColors
import com.hasyame.marvelchampions.data.settings.AppPreferences
import com.hasyame.marvelchampions.data.sync.CardSyncState
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.model.ThemeChoice
import com.hasyame.marvelchampions.ui.util.openExternalUrl
import com.hasyame.marvelchampions.ui.util.CONTACT_ADDRESS
import com.hasyame.marvelchampions.ui.util.sendContactEmail
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCollection: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = comicTopBarColors(),
                title = { Text(stringResource(R.string.destination_settings)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_collection)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_collection_summary))
                },
                modifier = Modifier.clickable(onClick = onOpenCollection),
            )
            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_card_language),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_card_language_summary),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardLocale.entries.forEach { locale ->
                    FilterChip(
                        selected = state.cardLocale == locale,
                        onClick = { viewModel.setCardLocale(locale) },
                        label = {
                            Text(
                                when (locale) {
                                    CardLocale.FRENCH -> stringResource(R.string.language_french)
                                    CardLocale.ENGLISH -> stringResource(R.string.language_english)
                                },
                            )
                        },
                    )
                }
            }
            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = state.themeChoice == choice,
                        onClick = { viewModel.setThemeChoice(choice) },
                        label = {
                            Text(
                                when (choice) {
                                    ThemeChoice.SYSTEM ->
                                        stringResource(R.string.settings_theme_system)

                                    ThemeChoice.LIGHT ->
                                        stringResource(R.string.settings_theme_light)

                                    ThemeChoice.DARK ->
                                        stringResource(R.string.settings_theme_dark)
                                },
                            )
                        },
                    )
                }
            }
            HorizontalDivider()

            CardUpdateSection(
                state = state,
                onSync = viewModel::syncCards,
                onCancel = viewModel::cancelSync,
            )
            HorizontalDivider()

            MusicSection(state = state, onMusicUrlChange = viewModel::setMusicUrl)
            HorizontalDivider()

            BggSection(
                state = state.bgg,
                isVerifying = state.bggVerifying,
                error = state.bggError,
                onConnect = viewModel::connectBgg,
                onDisconnect = viewModel::disconnectBgg,
                onModeChange = viewModel::setBggMode,
            )
            HorizontalDivider()

            val context = LocalContext.current
            var noMailApp by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_contact)) },
                supportingContent = { Text(stringResource(R.string.settings_contact_summary)) },
                modifier = Modifier.clickable {
                    noMailApp = !sendContactEmail(context, state.lastCardSync)
                },
            )
            if (noMailApp) {
                Text(
                    text = stringResource(R.string.settings_no_mail_app, CONTACT_ADDRESS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
            HorizontalDivider()

            var donateOpen by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_donate)) },
                supportingContent = { Text(stringResource(R.string.settings_donate_summary)) },
                modifier = Modifier.clickable { donateOpen = true },
            )

            // A row that does nothing when tapped reads as broken rather than as
            // unfinished, so it says which it is.
            if (donateOpen) {
                AlertDialog(
                    onDismissRequest = { donateOpen = false },
                    title = { Text(stringResource(R.string.settings_donate)) },
                    text = { Text(stringResource(R.string.settings_donate_pending)) },
                    confirmButton = {
                        TextButton(onClick = { donateOpen = false }) {
                            Text(stringResource(R.string.action_done))
                        }
                    },
                )
            }
        }
    }
}

/**
 * The playlist the play screen opens.
 *
 * A link rather than in-app playback: Spotify would need its own SDK and a
 * signed-in session to play inside another app, and Melodice's playlists are
 * YouTube. Handing the URL over keeps the user's own subscription and app in
 * charge.
 */
@Composable
private fun MusicSection(
    state: SettingsUiState,
    onMusicUrlChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(state.musicUrl) { mutableStateOf(state.musicUrl) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_music),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_music_summary),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                onMusicUrlChange(it)
            },
            singleLine = true,
            label = { Text(stringResource(R.string.settings_music_url)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    draft = AppPreferences.DEFAULT_MUSIC_URL
                    onMusicUrlChange(AppPreferences.DEFAULT_MUSIC_URL)
                },
            ) { Text(stringResource(R.string.settings_music_default)) }
            TextButton(
                onClick = { openExternalUrl(context, AppPreferences.MELODICE_URL) },
            ) { Text(stringResource(R.string.settings_music_browse)) }
        }
    }
}

@Composable
private fun CardUpdateSection(
    state: SettingsUiState,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.settings_update_cards),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = state.lastCardSync?.let {
                stringResource(
                    R.string.settings_last_sync,
                    DateFormat.getDateTimeInstance().format(Date(it)),
                )
            } ?: stringResource(R.string.settings_never_synced),
            style = MaterialTheme.typography.bodySmall,
        )

        when (val sync = state.syncState) {
            is CardSyncState.Running -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    text = syncStepLabel(sync),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }

            is CardSyncState.Failed -> {
                Text(
                    text = stringResource(
                        R.string.settings_sync_failed,
                        sync.message ?: stringResource(R.string.settings_sync_unknown_error),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onSync) { Text(stringResource(R.string.settings_update_now)) }
            }

            is CardSyncState.Cancelled -> {
                Text(
                    text = stringResource(R.string.settings_sync_cancelled),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onSync) { Text(stringResource(R.string.settings_update_now)) }
            }

            else -> Button(onClick = onSync) {
                Text(stringResource(R.string.settings_update_now))
            }
        }
    }
}

@Composable
private fun syncStepLabel(running: CardSyncState.Running): String {
    val locale = running.locale?.uppercase()
    return when (running.step) {
        "PACKS" -> stringResource(R.string.settings_sync_step_packs)
        "DOWNLOADING_CARDS" -> stringResource(R.string.settings_sync_step_downloading, locale ?: "")
        "STORING_CARDS" -> stringResource(R.string.settings_sync_step_storing, locale ?: "")
        else -> stringResource(R.string.settings_sync_step_starting)
    }
}
