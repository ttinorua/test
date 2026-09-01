package com.example.personalfinance.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.personalfinance.data.FinanceRepository
import com.example.personalfinance.ui.addtransaction.AddEditTransactionScreen
import com.example.personalfinance.ui.addtransaction.AddEditTransactionViewModel
import com.example.personalfinance.ui.categories.CategoriesScreen
import com.example.personalfinance.ui.categories.CategoriesViewModel
import com.example.personalfinance.ui.common.ViewModelFactory
import com.example.personalfinance.ui.dashboard.DashboardScreen
import com.example.personalfinance.ui.dashboard.DashboardViewModel
import com.example.personalfinance.ui.transactions.TransactionListScreen
import com.example.personalfinance.ui.transactions.TransactionListViewModel

private data class BottomDestination(val screen: Screen, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination(Screen.Dashboard, "Home", Icons.Filled.Home),
    BottomDestination(Screen.Transactions, "Transactions", Icons.Filled.List),
    BottomDestination(Screen.Categories, "Categories", Icons.Filled.Category)
)

@Composable
fun PersonalFinanceNavHost(repository: FinanceRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    val showBottomBar = bottomDestinations.any { it.screen.route == currentRoute?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute?.hierarchy?.any { it.route == destination.screen.route } == true,
                            onClick = {
                                navController.navigate(destination.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBottomBar) {
                FloatingActionButton(onClick = { navController.navigate(Screen.AddTransaction.route) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) {
                val viewModel: DashboardViewModel = viewModel(
                    factory = ViewModelFactory(DashboardViewModel::class.java to { DashboardViewModel(repository) })
                )
                DashboardScreen(
                    viewModel = viewModel,
                    onTransactionClick = { id -> navController.navigate(Screen.EditTransaction.createRoute(id)) },
                    onSeeAllClick = { navController.navigate(Screen.Transactions.route) }
                )
            }

            composable(Screen.Transactions.route) {
                val viewModel: TransactionListViewModel = viewModel(
                    factory = ViewModelFactory(TransactionListViewModel::class.java to { TransactionListViewModel(repository) })
                )
                TransactionListScreen(
                    viewModel = viewModel,
                    onTransactionClick = { id -> navController.navigate(Screen.EditTransaction.createRoute(id)) }
                )
            }

            composable(Screen.Categories.route) {
                val viewModel: CategoriesViewModel = viewModel(
                    factory = ViewModelFactory(CategoriesViewModel::class.java to { CategoriesViewModel(repository) })
                )
                CategoriesScreen(viewModel = viewModel)
            }

            composable(Screen.AddTransaction.route) {
                val viewModel: AddEditTransactionViewModel = viewModel(
                    factory = ViewModelFactory(
                        AddEditTransactionViewModel::class.java to { AddEditTransactionViewModel(repository, null) }
                    )
                )
                AddEditTransactionScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }

            composable(
                route = Screen.EditTransaction.route,
                arguments = listOf(navArgument(Screen.ARG_TRANSACTION_ID) { type = NavType.LongType })
            ) { entry ->
                val transactionId = entry.arguments?.getLong(Screen.ARG_TRANSACTION_ID) ?: 0L
                val viewModel: AddEditTransactionViewModel = viewModel(
                    key = "edit_$transactionId",
                    factory = ViewModelFactory(
                        AddEditTransactionViewModel::class.java to { AddEditTransactionViewModel(repository, transactionId) }
                    )
                )
                AddEditTransactionScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
        }
    }
}
