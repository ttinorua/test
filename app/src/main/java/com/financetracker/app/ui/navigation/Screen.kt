package com.financetracker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Home)
    data object Transactions : Screen("transactions", "Transactions", Icons.Filled.ReceiptLong)
    data object Overview : Screen("overview", "Spending", Icons.Filled.PieChart)
    data object ImportExport : Screen("import_export", "Import/Export", Icons.Filled.SwapVert)
    data object Settings : Screen("settings", "Accounts", Icons.Filled.AccountBalance)

    companion object {
        val bottomNavItems = listOf(Dashboard, Transactions, Overview, Settings, ImportExport)
    }
}
