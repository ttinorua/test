package com.financetracker.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
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
import com.financetracker.app.data.backup.AutoBackupSettings
import com.financetracker.app.data.bank.SupportedBanks
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.enablebanking.EnableBankingService
import com.financetracker.app.ui.navigation.Screen
import com.financetracker.app.ui.screens.ai.AskAiScreen
import com.financetracker.app.ui.screens.ai.AskAiViewModel
import com.financetracker.app.ui.screens.dashboard.DashboardScreen
import com.financetracker.app.ui.screens.dashboard.DashboardTransactionsScreen
import com.financetracker.app.ui.screens.dashboard.DashboardTransactionsViewModel
import com.financetracker.app.ui.screens.dashboard.DashboardViewModel
import com.financetracker.app.ui.screens.importexport.ImportExportViewModel
import com.financetracker.app.ui.screens.overview.CategoryOverviewScreen
import com.financetracker.app.ui.screens.overview.CategoryOverviewViewModel
import com.financetracker.app.ui.screens.overview.GroupTransactionsScreen
import com.financetracker.app.ui.screens.overview.GroupTransactionsViewModel
import com.financetracker.app.ui.screens.overview.TransactionsDrillDown
import com.financetracker.app.ui.screens.settings.EnableBankingViewModel
import com.financetracker.app.ui.screens.settings.SettingsScreen
import com.financetracker.app.ui.screens.settings.SettingsViewModel
import com.financetracker.app.ui.screens.transactions.TransactionsScreen
import com.financetracker.app.ui.screens.transactions.TransactionsViewModel
import com.financetracker.app.ui.screens.trends.TrendsScreen
import com.financetracker.app.ui.screens.trends.TrendsViewModel
import com.financetracker.app.ui.theme.PersonalFinanceTheme
import com.financetracker.app.util.PeriodOption
import com.financetracker.app.util.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

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
                                factory = ViewModelFactory { DashboardViewModel(repository, applicationContext) }
                            )
                            DashboardScreen(
                                vm,
                                onOpenAskAi = { navController.navigate("ask_ai") },
                                onOpenTrends = { navController.navigate("trends") },
                                onOpenTransactions = { type, categoryId, label, periodOption, customRange, includeAnticipated, accountId, uncategorizedOnly ->
                                    val typeName = type?.name ?: "NONE"
                                    val catId = categoryId ?: -1L
                                    val from = customRange?.first ?: -1L
                                    val to = customRange?.second ?: -1L
                                    val acctId = accountId ?: -1L
                                    navController.navigate(
                                        "dashboard_transactions/$typeName/$catId/${Uri.encode(label)}/" +
                                            "${periodOption.name}/$from/$to/$includeAnticipated/$acctId/$uncategorizedOnly"
                                    )
                                }
                            )
                        }
                        composable(
                            route = "dashboard_transactions/{type}/{categoryId}/{label}/{periodOption}/{from}/{to}/{includeAnticipated}/{accountId}/{uncategorizedOnly}",
                            arguments = listOf(
                                navArgument("type") { type = NavType.StringType },
                                navArgument("categoryId") { type = NavType.LongType },
                                navArgument("label") { type = NavType.StringType },
                                navArgument("periodOption") { type = NavType.StringType },
                                navArgument("from") { type = NavType.LongType },
                                navArgument("to") { type = NavType.LongType },
                                navArgument("includeAnticipated") { type = NavType.BoolType },
                                navArgument("accountId") { type = NavType.LongType },
                                navArgument("uncategorizedOnly") { type = NavType.BoolType }
                            )
                        ) { backStackEntry ->
                            val args = backStackEntry.arguments!!
                            val txType = args.getString("type")!!.let { if (it == "NONE") null else TransactionType.valueOf(it) }
                            val categoryId = args.getLong("categoryId").takeIf { it >= 0 }
                            val label = Uri.decode(args.getString("label")!!)
                            val periodOption = PeriodOption.valueOf(args.getString("periodOption")!!)
                            val from = args.getLong("from")
                            val to = args.getLong("to")
                            val includeAnticipated = args.getBoolean("includeAnticipated")
                            val accountId = args.getLong("accountId").takeIf { it >= 0 }
                            val uncategorizedOnly = args.getBoolean("uncategorizedOnly")
                            val customRange = if (periodOption == PeriodOption.CUSTOM && from >= 0 && to >= 0) {
                                from to to
                            } else {
                                null
                            }
                            val vm: DashboardTransactionsViewModel = viewModel(
                                factory = ViewModelFactory {
                                    DashboardTransactionsViewModel(
                                        repository,
                                        txType,
                                        categoryId,
                                        label,
                                        periodOption,
                                        customRange,
                                        includeAnticipated,
                                        accountId,
                                        uncategorizedOnly
                                    )
                                }
                            )
                            DashboardTransactionsScreen(vm, onClose = { navController.popBackStack() })
                        }
                        composable("trends") {
                            val vm: TrendsViewModel = viewModel(
                                factory = ViewModelFactory { TrendsViewModel(repository) }
                            )
                            TrendsScreen(
                                vm,
                                onBack = { navController.popBackStack() },
                                onOpenTransactions = { drillDown -> navController.navigate(drillDown.route()) }
                            )
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
                                onOpenTransactions = { drillDown -> navController.navigate(drillDown.route()) }
                            )
                        }
                        composable(
                            route = TransactionsDrillDown.ROUTE,
                            arguments = TransactionsDrillDown.arguments
                        ) { backStackEntry ->
                            val drillDown = TransactionsDrillDown.from(backStackEntry.arguments!!)
                            val vm: GroupTransactionsViewModel = viewModel(
                                factory = ViewModelFactory {
                                    GroupTransactionsViewModel(
                                        repository,
                                        drillDown.groupBy,
                                        drillDown.key,
                                        drillDown.periodOption,
                                        drillDown.customRange,
                                        drillDown.accountId,
                                        drillDown.type,
                                        drillDown.categoryFilter,
                                        drillDown.title
                                    )
                                }
                            )
                            GroupTransactionsScreen(vm, onClose = { navController.popBackStack() })
                        }
                        composable(Screen.Settings.route) {
                            val vm: SettingsViewModel = viewModel(
                                factory = ViewModelFactory { SettingsViewModel(repository, applicationContext) }
                            )
                            val importExportVm: ImportExportViewModel = viewModel(
                                factory = ViewModelFactory { ImportExportViewModel(repository, applicationContext) }
                            )
                            val enableBankingVm: EnableBankingViewModel = viewModel(
                                factory = ViewModelFactory { EnableBankingViewModel(repository, applicationContext) }
                            )
                            SettingsScreen(vm, importExportVm, enableBankingVm)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Handles the Enable Banking OAuth redirect (financetracker://enablebanking-callback?...),
     * which the browser opens after the user finishes MitID login on our GitHub Pages bridge
     * page. Any other intent (e.g. the normal launcher intent) is ignored. */
    private fun handleIncomingIntent(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "financetracker" && data.host == "onedrive-auth") {
            handleOneDriveSignIn(data)
            return
        }
        if (data.scheme != "financetracker" || data.host != "enablebanking-callback") return

        val error = data.getQueryParameter("error")
        if (error != null) {
            val description = data.getQueryParameter("error_description") ?: error
            Toast.makeText(this, "Bank connection failed: $description", Toast.LENGTH_LONG).show()
            return
        }

        val code = data.getQueryParameter("code") ?: return
        val state = data.getQueryParameter("state")
        lifecycleScope.launch {
            EnableBankingService.completeAuth(code, state)
                .onSuccess { accounts ->
                    val bankName = accounts.firstOrNull()?.bankId?.let { SupportedBanks.byId(it).displayName } ?: "bank"
                    Toast.makeText(
                        this@MainActivity,
                        "Connected ${accounts.size} $bankName account(s)",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .onFailure { e ->
                    Toast.makeText(this@MainActivity, "Bank connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    /** Finishes turning on the automatic backup to OneDrive once Microsoft's sign-in page sends
     * the browser back here (financetracker://onedrive-auth?code=...&state=...). */
    private fun handleOneDriveSignIn(data: Uri) {
        val code = data.getQueryParameter("code")
        if (code == null) {
            val reason = data.getQueryParameter("error_description") ?: data.getQueryParameter("error") ?: "cancelled"
            Toast.makeText(this, "OneDrive sign-in failed: $reason", Toast.LENGTH_LONG).show()
            return
        }
        val state = data.getQueryParameter("state")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AutoBackupSettings.completeOneDrive(applicationContext, code, state) }
            }
            val message = result.fold(
                onSuccess = { account -> "Weekly backup to OneDrive is on" + (account?.let { " ($it)" } ?: "") + "." },
                onFailure = { it.message ?: "OneDrive sign-in failed." }
            )
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}
