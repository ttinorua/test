package com.financetracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.prefs.DismissedRecurringExpenses
import com.financetracker.app.data.prefs.FixedExpenseCategories
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.AnticipatedExpense
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.SimilarTransactionsPrompt
import com.financetracker.app.util.anticipatedRecurringExpenses
import com.financetracker.app.util.countsTowardSpending
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.findSimilarTransactions
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardTransactionsUiState(
    val label: String = "",
    val transactions: List<TransactionWithDetails> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val showAnticipatedSections: Boolean = false,
    val anticipatedExpenses: List<AnticipatedExpense> = emptyList()
)

private data class DashboardTransactionsExtras(
    val accounts: List<Account>,
    val categories: List<Category>,
    val dismissedRecurring: Set<String>,
    val fixedCategoryIds: Set<Long>
)

/**
 * Shows every transaction in the dashboard's current period matching [type] and/or
 * [categoryId] (either or both null means "no filter on that field"). [includeAnticipated] is
 * true only for the Dashboard's own Expenses tile — the one drill-down whose total the
 * "Anticipate recurring bills" setting actually changes, for This month or Next month alike;
 * Budget rows and the category breakdown also open this same screen for EXPENSE/This-Month, but
 * their own totals never include anticipated amounts, so showing the upcoming/posted split there
 * would be misleading.
 */
class DashboardTransactionsViewModel(
    private val repository: FinanceRepository,
    type: TransactionType?,
    categoryId: Long?,
    label: String,
    periodOption: PeriodOption,
    customRange: Pair<Long, Long>?,
    includeAnticipated: Boolean
) : ViewModel() {

    val uiState: StateFlow<DashboardTransactionsUiState> = combine(
        repository.observeTransactions(),
        combine(
            BudgetSettings.shiftSalaryToNextMonth,
            BudgetSettings.excludeTransfersFromSpending,
            BudgetSettings.anticipateRecurringBills
        ) { shiftSalary, excludeTransfers, anticipateRecurring ->
            Triple(shiftSalary, excludeTransfers, anticipateRecurring)
        },
        combine(
            repository.observeAccounts(),
            repository.observeCategories(),
            DismissedRecurringExpenses.dismissed,
            FixedExpenseCategories.fixedCategoryIds
        ) { accounts, categories, dismissedRecurring, fixedCategoryIds ->
            DashboardTransactionsExtras(accounts, categories, dismissedRecurring, fixedCategoryIds)
        }
    ) { transactions, settings, extras ->
        val (shiftSalary, excludeTransfers, anticipateRecurring) = settings
        val (accounts, categories, dismissedRecurring, fixedCategoryIds) = extras
        val (from, to) = periodRange(periodOption, customRange)
        val filtered = transactions.filter { tx ->
            val effectiveDate =
                effectiveReportingDate(tx.date, tx.type, tx.mainCategoryName, tx.categoryName, shiftSalary)
            val inPeriod = effectiveDate >= from && effectiveDate < to
            val matchesType = type == null || tx.type == type
            val matchesCategory = categoryId == null || tx.categoryId == categoryId
            // Only applied when this list is specifically the "Expenses" drill-down (type ==
            // EXPENSE) — "All Transactions"/"Income" must stay unfiltered so they still sum to
            // the (never-filtered) net balance and income totals shown on the tiles above them.
            val countsIfRelevant = type != TransactionType.EXPENSE ||
                countsTowardSpending(tx.type, tx.mainCategoryName, tx.categoryName, excludeTransfers)
            inPeriod && matchesType && matchesCategory && countsIfRelevant
        }.sortedByDescending { it.date }

        val monthsAhead = when (periodOption) {
            PeriodOption.THIS_MONTH -> 0
            PeriodOption.NEXT_MONTH -> 1
            else -> null
        }
        val showAnticipated = includeAnticipated && anticipateRecurring && monthsAhead != null
        val anticipated = if (showAnticipated) {
            anticipatedRecurringExpenses(
                transactions,
                monthsAhead = monthsAhead!!,
                dismissedKeys = dismissedRecurring,
                fixedCategoryIds = fixedCategoryIds
            )
        } else {
            emptyList()
        }

        DashboardTransactionsUiState(
            label = label,
            transactions = filtered,
            accounts = accounts,
            categories = categories,
            showAnticipatedSections = showAnticipated,
            anticipatedExpenses = anticipated
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardTransactionsUiState(label = label))

    private val _similarPrompt = MutableStateFlow<SimilarTransactionsPrompt?>(null)
    val similarPrompt: StateFlow<SimilarTransactionsPrompt?> = _similarPrompt.asStateFlow()

    fun addTransaction(
        amount: Double,
        type: TransactionType,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        note: String
    ) {
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(amount = amount, type = type, accountId = accountId, categoryId = categoryId, date = date, note = note)
            )
        }
    }

    fun updateTransaction(
        id: Long,
        amount: Double,
        type: TransactionType,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        note: String
    ) {
        viewModelScope.launch {
            val allBefore = repository.observeTransactions().first()
            val original = allBefore.firstOrNull { it.id == id }
            repository.updateTransaction(
                Transaction(id = id, amount = amount, type = type, accountId = accountId, categoryId = categoryId, date = date, note = note)
            )
            if (original != null && categoryId != original.categoryId) {
                val similar = findSimilarTransactions(allBefore, original, categoryId)
                if (similar.isNotEmpty()) {
                    val categoryName = repository.observeCategories().first()
                        .firstOrNull { it.id == categoryId }?.name ?: "Uncategorized"
                    _similarPrompt.value = SimilarTransactionsPrompt(categoryId, categoryName, similar)
                }
            }
        }
    }

    /** Applies the pending [similarPrompt]'s new category to every transaction it listed. */
    fun applySimilarCategoryUpdate() {
        val prompt = _similarPrompt.value ?: return
        viewModelScope.launch {
            prompt.similar.forEach { tx ->
                repository.updateTransaction(
                    Transaction(
                        id = tx.id,
                        amount = tx.amount,
                        type = tx.type,
                        accountId = tx.accountId,
                        categoryId = prompt.newCategoryId,
                        date = tx.date,
                        note = tx.note
                    )
                )
            }
            _similarPrompt.value = null
        }
    }

    fun dismissSimilarPrompt() {
        _similarPrompt.value = null
    }

    /** Removes an anticipated (not-yet-posted) expense from "Upcoming expenses" — there's no
     * real transaction to delete, so this just remembers not to anticipate that specific bill
     * again until it actually posts or the month rolls over. See [DismissedRecurringExpenses]. */
    fun dismissAnticipated(expense: AnticipatedExpense) {
        DismissedRecurringExpenses.dismiss(expense.dismissKey)
    }

    fun deleteTransaction(transaction: TransactionWithDetails) {
        viewModelScope.launch {
            repository.deleteTransaction(
                Transaction(
                    id = transaction.id,
                    amount = transaction.amount,
                    type = transaction.type,
                    accountId = transaction.accountId,
                    categoryId = transaction.categoryId,
                    date = transaction.date,
                    note = transaction.note
                )
            )
        }
    }
}
