package com.example.personalfinance.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalfinance.data.Category
import com.example.personalfinance.data.FinanceRepository
import com.example.personalfinance.data.TransactionType
import com.example.personalfinance.data.TransactionWithCategory
import com.example.personalfinance.ui.common.formatDay
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionFilter(
    val type: TransactionType? = null,
    val categoryId: Long? = null
)

data class TransactionGroup(
    val dayLabel: String,
    val items: List<TransactionWithCategory>
)

data class TransactionListUiState(
    val groups: List<TransactionGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filter: TransactionFilter = TransactionFilter(),
    val isLoading: Boolean = true
)

class TransactionListViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter())

    val uiState: StateFlow<TransactionListUiState> = combine(
        repository.transactionsWithCategory,
        repository.categories,
        filter
    ) { transactions, categories, currentFilter ->
        val filtered = transactions.filter { item ->
            (currentFilter.type == null || item.transaction.type == currentFilter.type) &&
                (currentFilter.categoryId == null || item.transaction.categoryId == currentFilter.categoryId)
        }
        val zone = ZoneId.systemDefault()
        val groups = filtered
            .groupBy { Instant.ofEpochMilli(it.transaction.date).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) ->
                TransactionGroup(
                    dayLabel = formatDay(date.atStartOfDay(zone).toInstant().toEpochMilli()),
                    items = items
                )
            }

        TransactionListUiState(
            groups = groups,
            categories = categories,
            filter = currentFilter,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionListUiState())

    fun setTypeFilter(type: TransactionType?) {
        filter.value = filter.value.copy(type = type, categoryId = null)
    }

    fun setCategoryFilter(categoryId: Long?) {
        filter.value = filter.value.copy(categoryId = categoryId)
    }

    fun deleteTransaction(item: TransactionWithCategory) {
        viewModelScope.launch {
            repository.deleteTransaction(item.transaction)
        }
    }
}
