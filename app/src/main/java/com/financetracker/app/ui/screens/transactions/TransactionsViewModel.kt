package com.financetracker.app.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financetracker.app.data.db.entity.Account
import com.financetracker.app.data.db.entity.Category
import com.financetracker.app.data.db.entity.Transaction
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.db.entity.TransactionWithDetails
import com.financetracker.app.data.repository.FinanceRepository
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.periodRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

sealed interface TransactionListItem {
    data class MonthHeader(val label: String) : TransactionListItem
    data class DateHeader(val label: String, val balanceLabel: String?) : TransactionListItem
    data class Row(val tx: TransactionWithDetails, val runningBalance: Double?) : TransactionListItem
}

/** Local, user-driven filter state; kept separate from the DB-derived [TransactionsUiState]. */
data class TransactionFilterState(
    val selectedAccountId: Long? = null, // null = All accounts
    val periodOption: PeriodOption = PeriodOption.ALL_TIME,
    val customRange: Pair<Long, Long>? = null,
    val searchQuery: String = ""
)

data class TransactionsUiState(
    val allAccounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: TransactionFilterState = TransactionFilterState(),
    val displayItems: List<TransactionListItem> = emptyList()
)

class TransactionsViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilterState())
    val filter: StateFlow<TransactionFilterState> = _filter.asStateFlow()

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.observeTransactions(),
        repository.observeAccounts(),
        repository.observeCategories(),
        _filter
    ) { transactions, accounts, categories, filter ->
        TransactionsUiState(
            allAccounts = accounts,
            categories = categories,
            filter = filter,
            displayItems = buildDisplayItems(transactions, accounts, filter)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun selectAccount(accountId: Long?) {
        _filter.update { it.copy(selectedAccountId = accountId) }
    }

    fun selectPeriod(option: PeriodOption) {
        _filter.update { it.copy(periodOption = option) }
    }

    fun selectCustomRange(start: Long, endExclusive: Long) {
        _filter.update { it.copy(periodOption = PeriodOption.CUSTOM, customRange = start to endExclusive) }
    }

    fun setSearchQuery(query: String) {
        _filter.update { it.copy(searchQuery = query) }
    }

    private fun buildDisplayItems(
        transactions: List<TransactionWithDetails>,
        accounts: List<Account>,
        filter: TransactionFilterState
    ): List<TransactionListItem> {
        val (from, to) = periodRange(filter.periodOption, filter.customRange)
        val query = filter.searchQuery.trim()

        fun matchesSearch(tx: TransactionWithDetails): Boolean {
            if (query.isBlank()) return true
            if (tx.note.contains(query, ignoreCase = true)) return true
            if (tx.categoryName?.contains(query, ignoreCase = true) == true) return true
            if (tx.mainCategoryName?.contains(query, ignoreCase = true) == true) return true
            val signedAmount = (if (tx.type == TransactionType.EXPENSE) "-" else "") + Formatters.amount(tx.amount)
            if (signedAmount.contains(query, ignoreCase = true)) return true
            return Formatters.amount(tx.amount).contains(query, ignoreCase = true)
        }

        val runningBalanceByTxId: Map<Long, Double> = if (filter.selectedAccountId != null) {
            val account = accounts.firstOrNull { it.id == filter.selectedAccountId }
            val accountTx = transactions
                .filter { it.accountId == filter.selectedAccountId }
                .sortedWith(compareBy({ it.date }, { it.id }))
            var running = account?.initialBalance ?: 0.0
            val map = mutableMapOf<Long, Double>()
            accountTx.forEach { tx ->
                running += if (tx.type == TransactionType.INCOME) tx.amount else -tx.amount
                map[tx.id] = running
            }
            map
        } else {
            emptyMap()
        }

        val filtered = transactions
            .filter { filter.selectedAccountId == null || it.accountId == filter.selectedAccountId }
            .filter { it.date >= from && it.date < to }
            .filter(::matchesSearch)
            .sortedWith(compareByDescending<TransactionWithDetails> { it.date }.thenByDescending { it.id })

        val items = mutableListOf<TransactionListItem>()
        var lastMonthKey: Pair<Int, Int>? = null
        var lastDateKey: Long? = null

        filtered.forEach { tx ->
            val monthKey = yearMonthOf(tx.date)
            if (monthKey != lastMonthKey) {
                items += TransactionListItem.MonthHeader(Formatters.month(tx.date))
                lastMonthKey = monthKey
                lastDateKey = null
            }
            if (tx.date != lastDateKey) {
                val balance = runningBalanceByTxId[tx.id]
                items += TransactionListItem.DateHeader(
                    label = Formatters.fullDate(tx.date),
                    balanceLabel = balance?.let { Formatters.amount(it) }
                )
                lastDateKey = tx.date
            }
            items += TransactionListItem.Row(tx, runningBalanceByTxId[tx.id])
        }

        return items
    }

    private fun yearMonthOf(epochMillis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMillis
        return cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

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
                Transaction(
                    amount = amount,
                    type = type,
                    accountId = accountId,
                    categoryId = categoryId,
                    date = date,
                    note = note
                )
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
                Transaction(
                    id = id,
                    amount = amount,
                    type = type,
                    accountId = accountId,
                    categoryId = categoryId,
                    date = date,
                    note = note
                )
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
