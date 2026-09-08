package com.financetracker.app

import android.net.Uri
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.financetracker.app.ui.navigation.Screen
import com.financetracker.app.ui.screens.ai.AskAiScreen
import com.financetracker.app.ui.screens.ai.AskAiViewModel
import com.financetracker.app.ui.screens.dashboard.DashboardScreen
import com.financetracker.app.ui.screens.dashboard.DashboardViewModel
import com.financetracker.app.ui.screens.importexport.ImportExportViewModel
import com.financetracker.app.ui.screens.overview.CategoryOverviewScreen
import com.financetracker.app.ui.screens.overview.CategoryOverviewViewModel
import com.financetracker.app.ui.screens.overview.GroupTransactionsScreen
import com.financetracker.app.ui.screens.overview.GroupTransactionsViewModel
import com.financetracker.app.ui.screens.settings.SettingsScreen
import com.financetracker.app.ui.screens.settings.SettingsViewModel
import com.financetracker.app.ui.screens.transactions.TransactionsScreen
import com.financetracker.app.ui.screens.transactions.TransactionsViewModel
import com.financetracker.app.ui.theme.PersonalFinanceTheme
import com.financetracker.app.util.GroupByOption
import com.financetracker.app.util.PeriodOption
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
                            DashboardScreen(vm, onOpenAskAi = { navController.navigate("ask_ai") })
                        }
                        composable("ask_ai") {
                            val vm: AskAiViewModel = viewModel(
                                factory = ViewModelFactory { AskAiViewModel(repository) }
                            )
                            AskAiScreen(vm, onBack = { navController.popBackStack() })
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
                            CategoryOverviewScreen(
                                vm,
                                onEntryClick = { groupBy, key, periodOption, customRange ->
                                    val from = customRange?.first ?: -1L
                                    val to = customRange?.second ?: -1L
                                    navController.navigate(
                                        "group_transactions/${groupBy.name}/${Uri.encode(key)}/" +
                                            "${periodOption.name}/$from/$to"
                                    )
                                }
                            )
                        }
                        composable(
                            route = "group_transactions/{groupBy}/{key}/{periodOption}/{from}/{to}",
                            arguments = listOf(
                                navArgument("groupBy") { type = NavType.StringType },
                                navArgument("key") { type = NavType.StringType },
                                navArgument("periodOption") { type = NavType.StringType },
                                navArgument("from") { type = NavType.LongType },
                                navArgument("to") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val args = backStackEntry.arguments!!
                            val groupBy = GroupByOption.valueOf(args.getString("groupBy")!!)
                            val key = Uri.decode(args.getString("key")!!)
                            val periodOption = PeriodOption.valueOf(args.getString("periodOption")!!)
                            val from = args.getLong("from")
                            val to = args.getLong("to")
                            val customRange = if (periodOption == PeriodOption.CUSTOM && from >= 0 && to >= 0) {
                                from to to
                            } else {
                                null
                            }
                            val vm: GroupTransactionsViewModel = viewModel(
                                factory = ViewModelFactory {
                                    GroupTransactionsViewModel(repository, groupBy, key, periodOption, customRange)
                                }
                            )
                            GroupTransactionsScreen(vm, onClose = { navController.popBackStack() })
                        }
                        composable(Screen.Settings.route) {
                            val vm: SettingsViewModel = viewModel(
                                factory = ViewModelFactory { SettingsViewModel(repository) }
                            )
                            val importExportVm: ImportExportViewModel = viewModel(
                                factory = ViewModelFactory { ImportExportViewModel(repository, applicationContext) }
                            )
                            SettingsScreen(vm, importExportVm)
                        }
                    }
                }
            }
        }
    }
}
