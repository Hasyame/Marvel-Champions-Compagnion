package com.hasyame.marvelchampions.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hasyame.marvelchampions.R
import com.hasyame.marvelchampions.data.bgg.BggAccountState
import com.hasyame.marvelchampions.domain.model.BggReportingMode

/**
 * Connecting a BoardGameGeek account, and choosing when plays are sent.
 *
 * The warning is not boilerplate. BGG has no token or OAuth flow for logging
 * plays, so this is the one place the app asks for a password, and the player
 * deserves to know that before typing it rather than after.
 */
@Composable
fun BggSection(
    state: BggAccountState,
    isVerifying: Boolean,
    error: String?,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onModeChange: (BggReportingMode) -> Unit,
) {
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_bgg),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_bgg_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.isConnected) {
            ConnectedAccount(state, onDisconnect, onModeChange)
        } else {
            ConnectForm(isVerifying, error, onConnect)
        }
    }
}

@Composable
private fun ConnectedAccount(
    state: BggAccountState,
    onDisconnect: () -> Unit,
    onModeChange: (BggReportingMode) -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_bgg_connected_as, state.username),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )

    Text(
        text = stringResource(R.string.settings_bgg_when),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BggReportingMode.entries.forEach { mode ->
            FilterChip(
                selected = state.mode == mode,
                onClick = { onModeChange(mode) },
                label = {
                    Text(
                        when (mode) {
                            BggReportingMode.OFF -> stringResource(R.string.settings_bgg_off)
                            BggReportingMode.ASK -> stringResource(R.string.settings_bgg_ask)
                            BggReportingMode.ALWAYS -> stringResource(R.string.settings_bgg_always)
                        },
                    )
                },
            )
        }
    }
    Text(
        text = when (state.mode) {
            BggReportingMode.OFF -> stringResource(R.string.settings_bgg_off_detail)
            BggReportingMode.ASK -> stringResource(R.string.settings_bgg_ask_detail)
            BggReportingMode.ALWAYS -> stringResource(R.string.settings_bgg_always_detail)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) { Text(stringResource(R.string.settings_bgg_disconnect)) }
}

@Composable
private fun ConnectForm(
    isVerifying: Boolean,
    error: String?,
    onConnect: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        text = stringResource(R.string.settings_bgg_password_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )

    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text(stringResource(R.string.settings_bgg_username)) },
        singleLine = true,
        enabled = !isVerifying,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.settings_bgg_password)) },
        singleLine = true,
        enabled = !isVerifying,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    if (isVerifying) {
        CircularProgressIndicator(Modifier.padding(top = 8.dp))
    } else {
        Button(
            // Checked against BGG before anything is stored, so a typo is
            // caught while the player is still looking at the form.
            onClick = { onConnect(username.trim(), password) },
            enabled = username.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_bgg_connect)) }
    }
}
