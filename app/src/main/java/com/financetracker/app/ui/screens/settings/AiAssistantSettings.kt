package com.financetracker.app.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.AiProvider
import com.financetracker.app.data.ai.AiSettings
import com.financetracker.app.data.ai.AiSettingsState
import com.financetracker.app.ui.components.SetupGuide
import com.financetracker.app.ui.components.SetupGuideDialog
import com.financetracker.app.ui.components.SetupGuides

private val AI_GUIDES = listOf(SetupGuides.GEMINI, SetupGuides.GROQ, SetupGuides.CLAUDE)

private fun guideFor(provider: AiProvider): SetupGuide = when (provider) {
    AiProvider.GEMINI -> SetupGuides.GEMINI
    AiProvider.GROQ -> SetupGuides.GROQ
    AiProvider.CLAUDE -> SetupGuides.CLAUDE
}

/** Settings > General > AI assistant: which AI goes first, the fallback order, and each AI's key. */
@Composable
fun AiAssistantSettings(modifier: Modifier = Modifier) {
    val state by AiSettings.state.collectAsState()
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    var guide by remember { mutableStateOf<SetupGuide?>(null) }

    Column(modifier = modifier) {
        Text("AI assistant", style = MaterialTheme.typography.titleMedium)
        Text(
            "Used for categorizing transactions, dashboard insights and Ask AI. Choose which AI " +
                "to use first — if it fails or its free limit is used up, the next one takes over.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
        )
        Text("Use first", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiProvider.entries.forEach { provider ->
                FilterChip(
                    selected = state.primary == provider,
                    onClick = { AiSettings.setPrimary(provider) },
                    label = { Text(provider.label + if (provider.free) " (free)" else "") }
                )
            }
        }
        Text(
            if (state.chain.isEmpty()) {
                "No AI has a key yet — add one below."
            } else {
                "Order: " + state.chain.joinToString(" → ") { it.label } +
                    if (!state.hasKey(state.primary)) " (${state.primary.label} is skipped until it has a key)" else ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.chain.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                AiProvider.entries.forEachIndexed { index, provider ->
                    KeyRow(provider = provider, state = state, onEdit = { editing = provider })
                    if (index < AiProvider.entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        TextButton(onClick = { guide = guideFor(state.primary) }) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
            Text("How to get a key", modifier = Modifier.padding(start = 6.dp))
        }
        Text(
            "Gemini doesn't use your data for training in Europe; Groq doesn't keep it. Keys you add " +
                "are stored encrypted on this phone only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    editing?.let { provider ->
        KeyDialog(
            provider = provider,
            hasOwnKey = state.hasOwnKey(provider),
            onDismiss = { editing = null },
            onShowGuide = { guide = guideFor(provider) }
        )
    }
    guide?.let { SetupGuideDialog(guides = AI_GUIDES, initial = it, onDismiss = { guide = null }) }
}

@Composable
private fun KeyRow(provider: AiProvider, state: AiSettingsState, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(provider.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    state.hasOwnKey(provider) -> "Your own key"
                    state.hasBuiltInKey(provider) -> "Built into the app — nothing to set up"
                    provider.free -> "No key — free to create"
                    else -> "No key — optional, paid"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onEdit) { Text(if (state.hasOwnKey(provider)) "Change" else "Add key") }
    }
}

@Composable
private fun KeyDialog(provider: AiProvider, hasOwnKey: Boolean, onDismiss: () -> Unit, onShowGuide: () -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${provider.label} key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(if (hasOwnKey) "New key" else "Paste your key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onShowGuide) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                    Text("How to get a ${provider.label} key", modifier = Modifier.padding(start = 6.dp))
                }
                if (hasOwnKey) {
                    TextButton(onClick = {
                        AiSettings.setOwnKey(provider, null)
                        onDismiss()
                    }) { Text("Remove my key") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AiSettings.setOwnKey(provider, key)
                    onDismiss()
                },
                enabled = key.isNotBlank()
            ) { Text("Save key") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
