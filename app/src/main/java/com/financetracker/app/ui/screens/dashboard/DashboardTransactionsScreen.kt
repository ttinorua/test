package com.financetracker.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.financetracker.app.ui.components.TransactionsPopupScreen

@Composable
fun DashboardTransactionsScreen(viewModel: DashboardTransactionsViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val similarPrompt by viewModel.similarPrompt.collectAsState()
    TransactionsPopupScreen(
        title = state.label,
        transactions = state.transactions,
        accounts = state.accounts,
        categories = state.categories,
        showAnticipatedSections = state.showAnticipatedSections,
        anticipatedExpenses = state.anticipatedExpenses,
        onClose = onClose,
        onAddTransaction = viewModel::addTransaction,
        onUpdateTransaction = viewModel::updateTransaction,
        onDeleteTransaction = viewModel::deleteTransaction,
        onDismissAnticipated = viewModel::dismissAnticipated,
        similarPrompt = similarPrompt,
        onApplySimilarUpdate = viewModel::applySimilarCategoryUpdate,
        onDismissSimilarPrompt = viewModel::dismissSimilarPrompt
    )
}
