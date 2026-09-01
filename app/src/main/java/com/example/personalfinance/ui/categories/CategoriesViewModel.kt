package com.example.personalfinance.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalfinance.data.Category
import com.example.personalfinance.data.FinanceRepository
import com.example.personalfinance.data.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList()
)

val CategoryColorPalette = listOf(
    0xFF2E7D32, 0xFF00897B, 0xFF558B2F, 0xFFEF6C00, 0xFF1565C0,
    0xFF8E24AA, 0xFFC62828, 0xFFD81B60, 0xFF00838F, 0xFF6D4C41, 0xFF546E7A
)

class CategoriesViewModel(private val repository: FinanceRepository) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = repository.categories
        .map { categories ->
            CategoriesUiState(
                incomeCategories = categories.filter { it.type == TransactionType.INCOME },
                expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoriesUiState())

    fun addCategory(name: String, type: TransactionType, icon: String, color: Long) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCategory(
                Category(name = name.trim(), type = type, color = color, icon = icon)
            )
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }
}
