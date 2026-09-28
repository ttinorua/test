package com.financetracker.app.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.util.Formatters

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
                title = { Text("Ask your finances") },
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
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("e.g. How much did I spend on groceries last month?") },
                        modifier = Modifier.weight(1f),
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
                message = "Add your Anthropic API key to local.properties " +
                    "(ANTHROPIC_API_KEY=...) and rebuild to enable AI features."
            )
            state.messages.isEmpty() -> EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                message = "Ask anything about your spending, e.g. " +
                    "\"What did I spend the most on this month?\""
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages) { message ->
                    ChatBubble(message, onRespondToProposal = { accept -> viewModel.respondToProposal(message, accept) })
                }
                if (state.isSending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "Thinking...",
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
private fun ChatBubble(message: AiChatMessage, onRespondToProposal: (accept: Boolean) -> Unit) {
    val currencyCode by CurrencySettings.currencyCode.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    message.isError -> MaterialTheme.colorScheme.errorContainer
                    message.isUser -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)

                message.proposal?.let { proposal ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Proposed budget" + (proposal.accountName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelLarge
                    )
                    proposal.overallAmount?.let {
                        Text(
                            "Overall: ${Formatters.currency(it, currencyCode)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    proposal.categoryBudgets.forEach { cb ->
                        Text(
                            "${cb.mainCategory} • ${cb.category}: ${Formatters.currency(cb.amount, currencyCode)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Button(onClick = { onRespondToProposal(true) }) { Text("Apply") }
                        OutlinedButton(onClick = { onRespondToProposal(false) }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}
