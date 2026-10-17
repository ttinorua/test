package com.financetracker.app.ui.screens.transactions

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financetracker.app.FinanceApp
import com.financetracker.app.data.ai.MerchantLogoLookup
import com.financetracker.app.data.prefs.MerchantOverrides
import com.financetracker.app.ui.components.CompanyLogo
import com.financetracker.app.util.MerchantLogos
import com.financetracker.app.util.ReceiptSplits
import kotlinx.coroutines.flow.map

private enum class LogoChoice { AUTOMATIC, WEBSITE, NONE }

/**
 * Lets the person set the name and logo shown for a company, for every transaction whose text
 * contains a word they choose (pre-filled from [note]). Saved at once in [MerchantOverrides];
 * the transaction's own text isn't changed.
 */
@Composable
fun MerchantStyleDialog(note: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val existing = remember(note) { MerchantOverrides.ruleFor(note) }
    var keyword by remember { mutableStateOf(existing?.keyword ?: MerchantLogos.suggestedKeyword(note)) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var logoChoice by remember {
        mutableStateOf(
            when (existing?.domain) {
                null -> LogoChoice.AUTOMATIC
                "" -> LogoChoice.NONE
                else -> LogoChoice.WEBSITE
            }
        )
    }
    var website by remember { mutableStateOf(existing?.domain.orEmpty()) }

    // Every transaction's text, to count how many the chosen word covers.
    val repository = (context.applicationContext as FinanceApp).repository
    val notes by remember {
        repository.observeTransactions().map { list -> list.filter { !ReceiptSplits.isPart(it.id) }.map { it.note } }
    }.collectAsState(initial = emptyList())
    val matches = notes.count { MerchantLogos.containsWords(it, keyword) }
    val coversThis = MerchantLogos.containsWords(note, keyword)

    val domain = MerchantLogos.sanitizeDomain(website)
    val websiteError = logoChoice == LogoChoice.WEBSITE && domain == null
    // What "automatic" would show, as a hint.
    val automaticName = MerchantLogos.knownName(note)
        ?: MerchantLogos.merchantKey(note)?.let { MerchantLogoLookup.names.value[it] }?.ifBlank { null }
        ?: note

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logo and name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bank text: $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name in the list") },
                    placeholder = { Text(automaticName, maxLines = 1) },
                    supportingText = { Text("Leave empty for automatic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Logo", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(logoChoice == LogoChoice.AUTOMATIC, { logoChoice = LogoChoice.AUTOMATIC }, { Text("Automatic") })
                    FilterChip(logoChoice == LogoChoice.WEBSITE, { logoChoice = LogoChoice.WEBSITE }, { Text("Website") })
                    FilterChip(logoChoice == LogoChoice.NONE, { logoChoice = LogoChoice.NONE }, { Text("No logo") })
                }
                if (logoChoice == LogoChoice.WEBSITE) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = website,
                            onValueChange = { website = it.trim() },
                            label = { Text("Company website") },
                            placeholder = { Text("e.g. hbo.com") },
                            isError = websiteError && website.isNotEmpty(),
                            supportingText = { if (websiteError && website.isNotEmpty()) Text("Type just the address, e.g. hbo.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f)
                        )
                        CompanyLogo(domain = domain, contentDescription = "Logo preview", modifier = Modifier.size(40.dp)) {}
                    }
                } else if (logoChoice == LogoChoice.NONE) {
                    Text(
                        "The category icon is shown instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Apply to all transactions containing") },
                    isError = !coversThis,
                    supportingText = {
                        Text(
                            when {
                                !coversThis -> "This transaction's text doesn't contain that word"
                                matches == 1 -> "Matches 1 transaction"
                                else -> "Matches $matches transactions"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = coversThis && !websiteError,
                onClick = {
                    val chosenDomain = when (logoChoice) {
                        LogoChoice.AUTOMATIC -> null
                        LogoChoice.WEBSITE -> domain
                        LogoChoice.NONE -> ""
                    }
                    // A different word than before replaces the old choice instead of leaving it behind.
                    existing?.let { if (MerchantLogos.normalize(it.keyword) != MerchantLogos.normalize(keyword)) MerchantOverrides.remove(it.keyword) }
                    if (name.isBlank() && chosenDomain == null) {
                        MerchantOverrides.remove(keyword)
                    } else {
                        MerchantOverrides.set(keyword, name, chosenDomain)
                    }
                    Toast.makeText(
                        context,
                        if (matches == 1) "Updated 1 transaction" else "Updated $matches transactions",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = {
                        MerchantOverrides.remove(existing.keyword)
                        Toast.makeText(context, "Back to automatic", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
