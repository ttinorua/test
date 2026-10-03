package com.financetracker.app.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.ai.AiService
import com.financetracker.app.data.ai.ChatTurn
import com.financetracker.app.data.ai.advisor.AdvisorActions
import com.financetracker.app.data.ai.advisor.AdvisorCard
import com.financetracker.app.data.ai.advisor.AdvisorSnapshot
import com.financetracker.app.data.ai.advisor.AdvisorToolbox
import com.financetracker.app.data.ai.advisor.CardStatus
import com.financetracker.app.data.ai.agent.AiAgent
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.prefs.LoansAndGoals
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false,
    val cards: List<AdvisorCard> = emptyList()
)

data class AskAiUiState(
    val messages: List<AiChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val isConfigured: Boolean = AiService.isConfigured
)

/** How many earlier messages go along with each question (the advisor looks figures up again
 * with its functions, so older turns add little). */
private const val HISTORY_MESSAGES = 16

class AskAiViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    private val _isSending = MutableStateFlow(false)
    private var nextId = 1L

    val uiState: StateFlow<AskAiUiState> = combine(_messages, _isSending) { messages, sending ->
        AskAiUiState(messages = messages, isSending = sending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AskAiUiState())

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _isSending.value) return

        val history = historyTurns()
        _messages.update { it + AiChatMessage(nextId++, isUser = true, text = trimmed) }
        _isSending.value = true

        viewModelScope.launch {
            val snapshot = AdvisorSnapshot.load(repository)
            val toolbox = AdvisorToolbox(snapshot)
            AiAgent.run(
                systemPrompt = systemPrompt(snapshot),
                history = history,
                userMessage = trimmed,
                tools = toolbox.tools,
                onAttemptStart = toolbox::reset,
                execute = toolbox::execute
            ).onSuccess { run ->
                val cards = toolbox.cards
                val reply = run.text.ifBlank {
                    if (cards.isNotEmpty()) "Here's what I prepared — review it below." else
                        "I couldn't put together an answer for that. Try rephrasing it."
                }
                _messages.update { it + AiChatMessage(nextId++, isUser = false, text = reply, cards = cards) }
            }.onFailure { error ->
                _messages.update {
                    it + AiChatMessage(nextId++, isUser = false, text = error.message ?: "Something went wrong.", isError = true)
                }
            }
            _isSending.value = false
        }
    }

    /** Applies a prepared change (Apply on its card). */
    fun apply(messageId: Long, cardId: Int) {
        val card = card(messageId, cardId)?.takeIf { it.status == CardStatus.PENDING } ?: return
        val plan = card.plan ?: return
        viewModelScope.launch {
            runCatching { AdvisorActions.apply(repository, plan) }
                .onSuccess { (result, undo) ->
                    updateCard(messageId, cardId) { it.copy(status = CardStatus.APPLIED, undo = undo, result = result) }
                }
                .onFailure { e -> updateCard(messageId, cardId) { it.copy(result = "Couldn't apply: ${e.message}") } }
        }
    }

    fun undo(messageId: Long, cardId: Int) {
        val card = card(messageId, cardId)?.takeIf { it.status == CardStatus.APPLIED } ?: return
        val undo = card.undo ?: return
        viewModelScope.launch {
            runCatching { AdvisorActions.undo(repository, undo) }
                .onSuccess { result -> updateCard(messageId, cardId) { it.copy(status = CardStatus.UNDONE, undo = null, result = result) } }
                .onFailure { e -> updateCard(messageId, cardId) { it.copy(result = "Couldn't undo: ${e.message}") } }
        }
    }

    fun dismiss(messageId: Long, cardId: Int) {
        updateCard(messageId, cardId) { if (it.status == CardStatus.PENDING) it.copy(status = CardStatus.DISMISSED) else it }
    }

    private fun card(messageId: Long, cardId: Int) =
        _messages.value.firstOrNull { it.id == messageId }?.cards?.firstOrNull { it.id == cardId }

    private fun updateCard(messageId: Long, cardId: Int, change: (AdvisorCard) -> AdvisorCard) {
        _messages.update { list ->
            list.map { message ->
                if (message.id != messageId) message else message.copy(cards = message.cards.map { if (it.id == cardId) change(it) else it })
            }
        }
    }

    /** Earlier messages, with a note of each card and what the user did with it, so the advisor
     * knows what's already been prepared or applied. */
    private fun historyTurns(): List<ChatTurn> = _messages.value
        .filter { !it.isError }
        .takeLast(HISTORY_MESSAGES)
        .dropWhile { !it.isUser }
        .map { message ->
            val notes = message.cards.joinToString("") { card ->
                val status = when {
                    card.plan == null -> "shown"
                    card.status == CardStatus.PENDING -> "not applied yet"
                    card.status == CardStatus.APPLIED -> "applied by the user"
                    card.status == CardStatus.UNDONE -> "applied, then undone by the user"
                    else -> "dismissed by the user"
                }
                "\n[Card: ${card.title} — $status]"
            }
            ChatTurn(message.isUser, message.text + notes)
        }

    private fun systemPrompt(snapshot: AdvisorSnapshot): String {
        val today = SimpleDateFormat("EEEE d MMMM yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(snapshot.now)
        val currency = CurrencySettings.currencyCode.value
        return """
            You are the personal finance advisor inside the user's own finance-tracking app. You know personal finance, budgeting, saving, investing basics, loans and mortgages, and the economy, and you can look at and change the user's data in the app through your functions.

            Today is $today. Amounts are in $currency.
            Accounts: ${snapshot.accounts.joinToString { it.name }.ifBlank { "none yet" }}.
            Saved loans: ${LoansAndGoals.loans.value.size}, saved savings goals: ${LoansAndGoals.goals.value.size}.
            The user is most likely in Denmark (Danish banks and loan documents), so use Danish context where it helps — e.g. realkredit mortgages with a contribution rate (bidragssats), fixed-rate vs. adjustable-rate (F-kort, rentetilpasning) loans, interest-only periods (afdragsfrihed), refinancing (omlægning), the interest tax deduction (rentefradrag), aktiesparekonto, pension savings. When a rule or rate matters for the answer and you aren't sure it's current, say so.

            How to work:
            - Get every figure from your functions. Never guess numbers or do loan/savings maths in your head: use loan_calculator and savings_goal_calculator.
            - Use as many function calls as needed before answering (e.g. get_overview, then get_spending and get_recurring_payments for a savings review).
            - To change anything (categories, budgets, fixed costs, loans, goals), call the matching function. It only prepares a card the user reviews and applies with the Apply button — so never say a change is done; say what you've prepared and that they can tap Apply.
            - Before preparing a change, check the data first (e.g. find_transactions) so the card is right. If a request is unclear — which account, which period, which transactions — ask one short question instead.
            - Advice should be specific: name the categories, merchants and amounts, say how much it would save per month/year, and put the biggest wins first. Point out risks (budgets on track to be exceeded, price increases, rising rates, little buffer).
            - You're not a licensed financial adviser. For big decisions (refinancing, investing, pension) give your real analysis, then briefly suggest confirming with their bank or an independent adviser.
            - Reply in the user's language. Keep it short and easy to read: short paragraphs or "- " bullet lists, **bold** for key figures, no tables, no headings.
        """.trimIndent()
    }
}
