package com.financetracker.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.AiProvider
import com.financetracker.app.data.ai.AiSettings

/** Settings > General > AI assistant: choose Gemini or Claude, and enter your own key. */
@Composable
fun AiAssistantSettings(modifier: Modifier = Modifier) {
    val state by AiSettings.state.collectAsState()
    val provider = state.provider
    val hasOwnKey = if (provider == AiProvider.GEMINI) state.hasOwnGeminiKey else state.hasOwnClaudeKey
    val hasBuiltIn = if (provider == AiProvider.GEMINI) state.hasBuiltInGeminiKey else state.hasBuiltInClaudeKey
    var keyInput by remember(provider) { mutableStateOf("") }

    Column(modifier = modifier) {
        Text("AI assistant", style = MaterialTheme.typography.titleMedium)
        Text(
            "Used for categorizing transactions, dashboard insights and Ask AI.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { option ->
                FilterChip(
                    selected = provider == option,
                    onClick = { AiSettings.setProvider(option) },
                    label = { Text(if (option == AiProvider.GEMINI) "Gemini (free)" else "Claude (own key)") }
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        hasOwnKey -> "Using your own ${provider.label} key."
                        hasBuiltIn && provider == AiProvider.GEMINI -> "Using the app's free Gemini key — nothing to set up."
                        hasBuiltIn -> "Using the Claude key built into this app."
                        else -> "No ${provider.label} key yet — add one below."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (provider == AiProvider.GEMINI) {
                        "Gemini by Google: fast and free. On the free tier Google may use what's sent " +
                            "(transaction texts and amounts) to improve its products. For your own " +
                            "free key: aistudio.google.com → Get API key."
                    } else {
                        "Claude by Anthropic: the most careful categorizing; paid per use from your own " +
                            "account, and not used for training. Get a key at console.anthropic.com → API keys."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(if (hasOwnKey) "Replace your ${provider.label} key" else "Your ${provider.label} API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            AiSettings.setOwnKey(provider, keyInput)
                            keyInput = ""
                        },
                        enabled = keyInput.isNotBlank()
                    ) { Text("Save key") }
                    if (hasOwnKey) {
                        TextButton(onClick = { AiSettings.setOwnKey(provider, null) }) { Text("Remove my key") }
                    }
                }
                Text(
                    "Your key is stored encrypted on this phone only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
