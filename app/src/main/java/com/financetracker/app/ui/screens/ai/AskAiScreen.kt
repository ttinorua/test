package com.financetracker.app.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.advisor.AdvisorCard
import com.financetracker.app.data.ai.advisor.CardStatus
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.util.Formatters

private val SUGGESTIONS = listOf(
    "Categorize my uncategorized transactions",
    "Where can I save money?",
    "How am I doing on my budgets this month?",
    "Review my subscriptions",
    "Suggest a monthly budget for me",
    "Should I pay extra on my loan or save instead?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(viewModel: AskAiViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.isSending) {
        val lastIndex = state.messages.size - 1 + if (state.isSending) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI advisor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (state.isConfigured) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Ask, or tell me what to do…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 5,
                        enabled = !state.isSending
                    )
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(input)
                            input = ""
                        },
                        enabled = !state.isSending && input.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        when {
            !state.isConfigured -> EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = "No AI is set up yet. Add a key in Settings > General > AI assistant."
            )
            state.messages.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Your personal finance advisor. Ask about your spending, budgets, loans and savings — or ask it to " +
                        "do things like categorizing transactions or setting budgets. Changes are shown first, and only " +
                        "happen when you tap Apply.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SUGGESTIONS.forEach { suggestion ->
                    AssistChip(onClick = { viewModel.sendMessage(suggestion) }, label = { Text(suggestion) })
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        message,
                        onApply = { viewModel.apply(message.id, it) },
                        onDismiss = { viewModel.dismiss(message.id, it) },
                        onUndo = { viewModel.undo(message.id, it) }
                    )
                }
                if (state.isSending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Looking into it…",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AiChatMessage, onApply: (Int) -> Unit, onDismiss: (Int) -> Unit, onUndo: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(if (message.isUser) 0.85f else 0.95f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        message.isError -> MaterialTheme.colorScheme.errorContainer
                        message.isUser -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = if (message.isUser) AnnotatedString(message.text) else simpleMarkdown(message.text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
            message.cards.forEach { card ->
                AdvisorCardView(card, onApply = { onApply(card.id) }, onDismiss = { onDismiss(card.id) }, onUndo = { onUndo(card.id) })
            }
        }
    }
}

@Composable
private fun AdvisorCardView(card: AdvisorCard, onApply: () -> Unit, onDismiss: () -> Unit, onUndo: () -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleSmall)
            card.lines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (card.transactions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val visible = if (showAll) card.transactions else card.transactions.take(8)
                visible.forEach { TransactionLine(it) }
                if (card.transactions.size > 8) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Show less" else "Show all ${card.transactions.size}")
                    }
                }
            }
            card.result?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (card.plan != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    when (card.status) {
                        CardStatus.PENDING -> {
                            Button(onClick = onApply) { Text("Apply") }
                            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                        }
                        CardStatus.APPLIED -> OutlinedButton(onClick = onUndo) { Text("Undo") }
                        CardStatus.UNDONE -> {}
                        CardStatus.DISMISSED -> Text(
                            "Dismissed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionLine(tx: TransactionWithDetails) {
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.note.ifBlank { "(no text)" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formatters.date(tx.date)} · ${tx.categoryName ?: "Uncategorized"} · ${tx.accountName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            (if (tx.type == TransactionType.INCOME) "+" else "-") + Formatters.currency(tx.amount, currencyCode),
            style = MaterialTheme.typography.bodySmall,
            color = if (tx.type == TransactionType.INCOME) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** The little Markdown the advisor uses: **bold**, "- " / "* " bullets and "#" headings. */
private fun simpleMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        var line = rawLine
        val heading = line.trimStart().startsWith("#")
        if (heading) line = line.trimStart().trimStart('#').trim()
        val bullet = Regex("^(\\s*)[-*•]\\s+").find(line)
        if (bullet != null) {
            append(bullet.groupValues[1] + "•  ")
            line = line.substring(bullet.range.last + 1)
        }
        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            if (i % 2 == 1 || heading) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
        }
    }
}
