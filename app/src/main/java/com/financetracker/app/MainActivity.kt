package com.financetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.financetracker.app.ui.navigation.Screen
import com.financetracker.app.ui.screens.dashboard.DashboardScreen
import com.financetracker.app.ui.screens.dashboard.DashboardViewModel
import com.financetracker.app.ui.screens.importexport.ImportExportScreen
import com.financetracker.app.ui.screens.importexport.ImportExportViewModel
import com.financetracker.app.ui.screens.overview.CategoryOverviewScreen
import com.financetracker.app.ui.screens.overview.CategoryOverviewViewModel
import com.financetracker.app.ui.screens.settings.SettingsScreen
import com.financetracker.app.ui.screens.settings.SettingsViewModel
import com.financetracker.app.ui.screens.transactions.TransactionsScreen
import com.financetracker.app.ui.screens.transactions.TransactionsViewModel
import com.financetracker.app.ui.theme.PersonalFinanceTheme
import com.financetracker.app.util.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as FinanceApp).repository

        setContent {
            PersonalFinanceTheme {
                val navController = rememberNavController()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val backStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = backStackEntry?.destination

                            Screen.bottomNavItems.forEach { screen ->
                                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                    ) {
                        composable(Screen.Dashboard.route) {
                            val vm: DashboardViewModel = viewModel(
                                factory = ViewModelFactory { DashboardViewModel(repository) }
                            )
                            DashboardScreen(vm)
                        }
                        composable(Screen.Transactions.route) {
                            val vm: TransactionsViewModel = viewModel(
                                factory = ViewModelFactory { TransactionsViewModel(repository) }
                            )
                            TransactionsScreen(vm)
                        }
                        composable(Screen.Overview.route) {
                            val vm: CategoryOverviewViewModel = viewModel(
                                factory = ViewModelFactory { CategoryOverviewViewModel(repository) }
                            )
                            CategoryOverviewScreen(vm)
                        }
                        composable(Screen.ImportExport.route) {
                            val vm: ImportExportViewModel = viewModel(
                                factory = ViewModelFactory { ImportExportViewModel(repository, applicationContext) }
                            )
                            ImportExportScreen(vm)
                        }
                        composable(Screen.Settings.route) {
                            val vm: SettingsViewModel = viewModel(
                                factory = ViewModelFactory { SettingsViewModel(repository) }
                            )
                            SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
