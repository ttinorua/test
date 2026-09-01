package com.example.personalfinance.ui.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Transactions : Screen("transactions")
    data object Categories : Screen("categories")
    data object AddTransaction : Screen("transaction/new")
    data object EditTransaction : Screen("transaction/{transactionId}") {
        fun createRoute(transactionId: Long) = "transaction/$transactionId"
    }

    companion object {
        const val ARG_TRANSACTION_ID = "transactionId"
    }
}
