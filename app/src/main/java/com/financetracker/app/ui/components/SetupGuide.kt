package com.financetracker.app.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.enablebanking.ENABLE_BANKING_REDIRECT_URL

/** A link the guide opens in the browser. */
data class GuideLink(val label: String, val url: String)

/** One setup guide: a few numbered steps, the websites they need, and optionally a value to copy
 * (e.g. the redirect URL to paste into a form). */
data class SetupGuide(
    val tab: String,
    val title: String,
    val note: String,
    val steps: List<String>,
    val links: List<GuideLink>,
    val copyLabel: String? = null,
    val copyValue: String? = null
)

object SetupGuides {
    val GEMINI = SetupGuide(
        tab = "Gemini",
        title = "Gemini — free, no card needed",
        note = "Created with your Google account.",
        steps = listOf(
            "Tap Open Google AI Studio and sign in with your Google account.",
            "Tap Create API key and accept the terms if asked.",
            "Copy the key.",
            "Back in the app, paste it in the Gemini key field and tap Save key."
        ),
        links = listOf(GuideLink("Open Google AI Studio", "https://aistudio.google.com/app/apikey"))
    )

    val GROQ = SetupGuide(
        tab = "Groq",
        title = "Groq — free, no card needed",
        note = "Created with Google or your email.",
        steps = listOf(
            "Tap Open Groq and sign up with Google or your email.",
            "Tap Create API Key, name it e.g. \"Finance Tracker\" and confirm.",
            "Copy the key straight away — it's shown only once.",
            "Back in the app, paste it in the Groq key field and tap Save key."
        ),
        links = listOf(GuideLink("Open Groq", "https://console.groq.com/keys"))
    )

    val CLAUDE = SetupGuide(
        tab = "Claude",
        title = "Claude — paid, pay as you go",
        note = "You only pay for what the app uses.",
        steps = listOf(
            "Tap Open Anthropic and create an account.",
            "Under Billing, add credit (e.g. \$5).",
            "Under API keys, tap Create key and copy it — it's shown only once.",
            "Back in the app, paste it in the Claude key field and tap Save key."
        ),
        links = listOf(GuideLink("Open Anthropic", "https://platform.claude.com/settings/keys"))
    )

    val ENABLE_BANKING = SetupGuide(
        tab = "Bank",
        title = "Your own Enable Banking app",
        note = "Lets you connect your own bank accounts. Easiest on a computer.",
        steps = listOf(
            "Tap Sign in to Enable Banking and enter your email — you'll get a sign-in link, no password.",
            "Open API applications and register a new application: choose Production, any name " +
                "(your bank shows it when you approve), and paste the redirect URL below.",
            "Keep \"generate in browser\" for the private key. A .pem file downloads — its file name " +
                "is your Application ID.",
            "In the Control Panel, link your own bank accounts to the application.",
            "Get the .pem file onto this phone (e.g. Google Drive or email it to yourself).",
            "In the app: Settings > Bank > Your own Enable Banking app — paste the Application ID, " +
                "choose the .pem file and tap Save. Then connect your bank."
        ),
        links = listOf(
            GuideLink("Sign in to Enable Banking", "https://enablebanking.com/sign-in/"),
            GuideLink("Open API applications", "https://enablebanking.com/cp/applications")
        ),
        copyLabel = "Redirect URL",
        copyValue = ENABLE_BANKING_REDIRECT_URL
    )
}

/** Shows [guides] with tabs to switch between them, starting on [initial]. */
@Composable
fun SetupGuideDialog(guides: List<SetupGuide>, initial: SetupGuide, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to set up") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (guides.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        guides.forEach { guide ->
                            FilterChip(
                                selected = guide == current,
                                onClick = { current = guide },
                                label = { Text(guide.tab) }
                            )
                        }
                    }
                }
                Text(current.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    current.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                current.steps.forEachIndexed { index, step ->
                    Row {
                        Text(
                            "${index + 1}.",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (current.copyValue != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    current.copyLabel.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(current.copyValue.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(current.copyValue.orEmpty()))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Copy", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
                current.links.forEach { link ->
                    OutlinedButton(
                        onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(link.label, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Text(
                    "Keep your key private — don't share it or post it anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
