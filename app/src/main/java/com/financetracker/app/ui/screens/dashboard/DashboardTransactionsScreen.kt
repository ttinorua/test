package com.financetracker.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.financetracker.app.ui.components.TransactionsPopupScreen

@Composable
fun DashboardTransactionsScreen(viewModel: DashboardTransactionsViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    TransactionsPopupScreen(title = state.label, transactions = state.transactions, onClose = onClose)
}
