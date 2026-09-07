package com.financetracker.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.ai.ChatTurn
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class AiChatMessage(val isUser: Boolean, val text: String, val isError: Boolean = false)

data class AskAiUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val isConfigured: Boolean = ClaudeService.isConfigured
)

private const val MAX_TRANSACTIONS_IN_CONTEXT = 3000

class AskAiViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    private val _isSending = MutableStateFlow(false)

    val uiState: StateFlow<AskAiUiState> = combine(_messages, _isSending) { messages, sending ->
        AskAiUiState(messages = messages, isSending = sending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskAiUiState())

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return

        _messages.update { it + AiChatMessage(isUser = true, text = trimmed) }
        _isSending.value = true

        viewModelScope.launch {
            val history = _messages.value.dropLast(1).map { ChatTurn(it.isUser, it.text) }
            val transactions = repository.observeTransactions().first()
            val accounts = repository.observeAccounts().first()
            val context = buildContext(transactions, accounts)

            ClaudeService.chat(context, history, trimmed)
                .onSuccess { reply ->
                    _messages.update { it + AiChatMessage(isUser = false, text = reply) }
                }
                .onFailure { error ->
                    _messages.update {
                        it + AiChatMessage(
                            isUser = false,
                            text = error.message ?: "Something went wrong.",
                            isError = true
                        )
                    }
                }
            _isSending.value = false
        }
    }

    private fun buildContext(
        transactions: List<TransactionWithDetails>,
        accounts: List<com.financetracker.app.data.db.entity.Account>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val todayFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return buildString {
            appendLine("You are a helpful personal finance assistant built into the user's own finance-tracking app.")
            appendLine("Today's date is ${todayFormat.format(System.currentTimeMillis())}.")
            appendLine("Display currency: ${CurrencySettings.currencyCode.value}.")
            appendLine(
                "Answer questions using ONLY the data below. Be concise and specific with numbers. " +
                    "If the data doesn't support an answer, say so instead of guessing."
            )
            appendLine()
            appendLine("ACCOUNTS (name, starting balance):")
            accounts.forEach { appendLine("- ${it.name}: ${it.initialBalance}") }
            appendLine()
            appendLine("TRANSACTIONS (Date|Account|MainCategory|Category|Type|Amount|Note), newest first:")
            transactions.take(MAX_TRANSACTIONS_IN_CONTEXT).forEach { tx ->
                append(dateFormat.format(tx.date)).append('|')
                    .append(tx.accountName).append('|')
                    .append(tx.mainCategoryName ?: "Uncategorized").append('|')
                    .append(tx.categoryName ?: "Uncategorized").append('|')
                    .append(tx.type.name).append('|')
                    .append(tx.amount).append('|')
                    .append(tx.note.replace('\n', ' ').replace('|', '/'))
                    .append('\n')
            }
        }
    }
}
