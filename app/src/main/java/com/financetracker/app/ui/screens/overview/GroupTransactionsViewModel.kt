package com.financetracker.app.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.SimilarTransactionsPrompt
import com.financetracker.app.util.countsTowardSpending
import com.financetracker.app.util.effectiveReportingDate
import com.financetracker.app.util.findSimilarTransactions
import com.financetracker.app.util.groupKeyOf
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupTransactionsUiState(
    val groupLabel: String = "",
    val transactions: List<TransactionWithDetails> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList()
)

/** Shows every transaction in [key]'s [groupBy] bucket for the period the Spending tab was on. */
class GroupTransactionsViewModel(
    private val repository: FinanceRepository,
    groupBy: GroupByOption,
    key: String,
    periodOption: PeriodOption,
    customRange: Pair<Long, Long>?
) : ViewModel() {

    val uiState: StateFlow<GroupTransactionsUiState> = combine(
        repository.observeTransactions(),
        BudgetSettings.shiftSalaryToNextMonth,
        BudgetSettings.excludeTransfersFromSpending,
        repository.observeAccounts(),
        repository.observeCategories()
    ) { transactions, shiftSalary, excludeTransfers, accounts, categories ->
        val (from, to) = periodRange(periodOption, customRange)
        val filtered = transactions.filter {
            val effectiveDate =
                effectiveReportingDate(it.date, it.type, it.mainCategoryName, it.categoryName, shiftSalary)
            val inPeriod = effectiveDate >= from && effectiveDate < to && groupKeyOf(it, groupBy) == key
            inPeriod && countsTowardSpending(it.type, it.mainCategoryName, it.categoryName, excludeTransfers)
        }.sortedByDescending { it.date }

        GroupTransactionsUiState(groupLabel = key, transactions = filtered, accounts = accounts, categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupTransactionsUiState(groupLabel = key))

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
