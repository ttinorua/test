package com.financetracker.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.AnticipatedExpense
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.anticipatedRecurringExpenses
import com.financetracker.app.util.countsTowardSpending
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/**
 * Shows every transaction in the dashboard's current period matching [type] and/or
 * [categoryId] (either or both null means "no filter on that field"). [includeAnticipated] is
 * true only for the Dashboard's own Expenses tile — the one drill-down whose total the
 * "Anticipate recurring bills" setting actually changes; Budget rows and the category breakdown
 * also open this same screen for EXPENSE/This-Month, but their own totals never include
 * anticipated amounts, so showing the upcoming/posted split there would be misleading.
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
        repository.observeAccounts(),
        repository.observeCategories()
    ) { transactions, settings, accounts, categories ->
        val (shiftSalary, excludeTransfers, anticipateRecurring) = settings
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

        val showAnticipated = includeAnticipated && anticipateRecurring && periodOption == PeriodOption.THIS_MONTH
        val anticipated = if (showAnticipated) anticipatedRecurringExpenses(transactions) else emptyList()

        DashboardTransactionsUiState(
            label = label,
            transactions = filtered,
            accounts = accounts,
            categories = categories,
            showAnticipatedSections = showAnticipated,
            anticipatedExpenses = anticipated
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardTransactionsUiState(label = label))

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
            repository.updateTransaction(
                Transaction(id = id, amount = amount, type = type, accountId = accountId, categoryId = categoryId, date = date, note = note)
            )
        }
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
