package com.financetracker.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.ai.BudgetProposal
import com.financetracker.app.data.ai.ChatTurn
import com.financetracker.app.data.ai.ClaudeService
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetLimits
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.Formatters
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

data class AiChatMessage(
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    val proposal: BudgetProposal? = null
)

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
            val categories = repository.observeCategories().first()
            val balances = accounts.associate { it.id to repository.getAccountBalance(it) }
            val context = buildContext(transactions, accounts, categories, balances)

            ClaudeService.chatWithBudgetTool(context, history, trimmed)
                .onSuccess { result ->
                    val text = when {
                        result.text.isNotBlank() -> result.text
                        result.proposal != null -> result.proposal.summary ?: "Here's a proposed budget:"
                        else -> "I couldn't put together a useful answer for that — try rephrasing, " +
                            "or ask about a specific category or time period."
                    }
                    _messages.update { it + AiChatMessage(isUser = false, text = text, proposal = result.proposal) }
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

    /** Applies a budget the user accepted from a chat proposal, matching its account/category
     * names back to real ids (the AI only ever deals in names, never ids). */
    fun respondToProposal(message: AiChatMessage, accept: Boolean) {
        val proposal = message.proposal ?: return
        _messages.update { list -> list.map { if (it === message) it.copy(proposal = null) else it } }
        if (!accept) {
            _messages.update { it + AiChatMessage(isUser = false, text = "Okay, I won't apply that budget.") }
            return
        }
        viewModelScope.launch {
            val accounts = repository.observeAccounts().first()
            val categories = repository.observeCategories().first()
            val accountId = proposal.accountName
                ?.takeUnless { it.equals("All accounts", ignoreCase = true) }
                ?.let { name -> accounts.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id }

            var appliedCount = 0
            proposal.overallAmount?.let {
                BudgetLimits.setOverallBudget(accountId, it)
                appliedCount++
            }
            proposal.categoryBudgets.forEach { entry ->
                val category = categories.firstOrNull {
                    it.mainCategory.equals(entry.mainCategory, ignoreCase = true) &&
                        it.name.equals(entry.category, ignoreCase = true)
                }
                if (category != null) {
                    BudgetLimits.setCategoryBudget(category.id, accountId, entry.amount)
                    appliedCount++
                }
            }

            val scopeLabel = accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name } ?: "All accounts"
            val confirmation = if (appliedCount == 0) {
                "Couldn't match that proposal to your accounts/categories — nothing was applied."
            } else {
                "✓ Budget applied ($scopeLabel)."
            }
            _messages.update { it + AiChatMessage(isUser = false, text = confirmation) }
        }
    }

    private fun buildContext(
        transactions: List<TransactionWithDetails>,
        accounts: List<Account>,
        categories: List<Category>,
        balances: Map<Long, Double>
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
            appendLine(
                "Only call the propose_budget tool when the user explicitly asks you to set up, " +
                    "create, or recommend a specific numeric budget (e.g. \"set a budget for me\", " +
                    "\"recommend a monthly budget\", \"how much should I budget for groceries\"). " +
                    "Analyze the TRANSACTIONS data below for whatever time range and categories " +
                    "they mention (or a sensible recent window if they don't specify one — never " +
                    "assume a fixed period like 12 months), and use exact account and category " +
                    "names from the lists below. This only shows the user a proposal to confirm " +
                    "— never claim you've already set a budget."
            )
            appendLine(
                "For open-ended questions like \"where can I cut back\", \"where am I " +
                    "overspending\", or \"how do I save more\", answer directly in plain text " +
                    "instead: name specific categories and real amounts from the TRANSACTIONS " +
                    "data below. Only use propose_budget once the user wants that turned into " +
                    "actual numeric limits."
            )
            appendLine()
            appendLine("ACCOUNTS (name, current balance as of today):")
            accounts.forEach { appendLine("- ${it.name}: ${Formatters.amount(balances[it.id] ?: it.initialBalance)}") }
            appendLine()
            appendLine("CATEGORIES (MainCategory|Category|Type):")
            categories.forEach { appendLine("- ${it.mainCategory}|${it.name}|${it.type}") }
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
