package com.example.personalfinance.ui.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalfinance.data.Category
import com.example.personalfinance.data.FinanceRepository
import com.example.personalfinance.data.Transaction
import com.example.personalfinance.data.TransactionType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AddEditTransactionUiState(
    val transactionId: Long? = null,
    val amountText: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: Long? = null,
    val date: Long = todayEpochMillis(),
    val note: String = "",
    val allCategories: List<Category> = emptyList(),
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
) {
    val categoriesForType: List<Category> get() = allCategories.filter { it.type == type }
    val amount: Double? get() = amountText.toDoubleOrNull()
    val isValid: Boolean get() = (amount ?: 0.0) > 0.0 && categoryId != null
}

private fun todayEpochMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

class AddEditTransactionViewModel(
    private val repository: FinanceRepository,
    private val editingTransactionId: Long?
) : ViewModel() {

    private val form = MutableStateFlow(AddEditTransactionUiState(transactionId = editingTransactionId))

    val uiState: StateFlow<AddEditTransactionUiState> = combine(
        form,
        repository.categories
    ) { formState, categories ->
        formState.copy(allCategories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), form.value)

    init {
        if (editingTransactionId != null) {
            viewModelScope.launch {
                repository.getTransaction(editingTransactionId)?.let { transaction ->
                    form.value = form.value.copy(
                        amountText = trimTrailingZero(transaction.amount),
                        type = transaction.type,
                        categoryId = transaction.categoryId,
                        date = transaction.date,
                        note = transaction.note
                    )
                }
            }
        }
    }

    fun setAmountText(value: String) {
        val sanitized = value.filterIndexed { index, c -> c.isDigit() || (c == '.' && value.indexOf('.') == index) }
        form.value = form.value.copy(amountText = sanitized)
    }

    fun setType(type: TransactionType) {
        // Categories are type-specific, so a category chosen under the old type never
        // applies to the new one.
        form.value = form.value.copy(type = type, categoryId = null)
    }

    fun setCategory(categoryId: Long) {
        form.value = form.value.copy(categoryId = categoryId)
    }

    fun setDate(epochMillis: Long) {
        form.value = form.value.copy(date = epochMillis)
    }

    fun setNote(note: String) {
        form.value = form.value.copy(note = note)
    }

    fun save() {
        val current = uiState.value
        val amount = current.amount ?: return
        val categoryId = current.categoryId ?: return
        viewModelScope.launch {
            val transaction = Transaction(
                id = current.transactionId ?: 0,
                amount = amount,
                type = current.type,
                categoryId = categoryId,
                date = current.date,
                note = current.note.trim()
            )
            if (current.transactionId == null) {
                repository.addTransaction(transaction)
            } else {
                repository.updateTransaction(transaction)
            }
            form.value = form.value.copy(isSaved = true)
        }
    }

    fun delete() {
        val id = uiState.value.transactionId ?: return
        viewModelScope.launch {
            repository.getTransaction(id)?.let { repository.deleteTransaction(it) }
            form.value = form.value.copy(isDeleted = true)
        }
    }

    private fun trimTrailingZero(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
}
