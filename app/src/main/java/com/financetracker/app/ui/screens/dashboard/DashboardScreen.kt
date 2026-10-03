package com.financetracker.app.ui.screens.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.financetracker.app.data.advisor.AttentionMonitor
import com.financetracker.app.ui.screens.ai.AdvisorLaunch
import com.financetracker.app.util.advisor.AttentionFilter
import com.financetracker.app.util.advisor.AttentionItem
import com.financetracker.app.util.advisor.AttentionLevel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financetracker.app.data.ai.AiService
import com.financetracker.app.data.ai.InsightCard
import com.financetracker.app.data.ai.InsightTone
import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.prefs.AiInsightsCache
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.ui.components.AccountSelectorChip
import com.financetracker.app.ui.components.BudgetProgressRow
import com.financetracker.app.ui.components.CategoryBreakdownList
import com.financetracker.app.ui.components.EmptyState
import com.financetracker.app.ui.components.PeriodSelectorChip
import com.financetracker.app.ui.components.SummaryCard
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import com.financetracker.app.util.Formatters
import com.financetracker.app.util.PeriodOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenAskAi: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenTransactions: (
        type: TransactionType?,
        categoryId: Long?,
        label: String,
        periodOption: PeriodOption,
        customRange: Pair<Long, Long>?,
        includeAnticipated: Boolean,
        accountId: Long?,
        uncategorizedOnly: Boolean
    ) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val currencyCode by CurrencySettings.currencyCode.collectAsState()
    val insights by AiInsightsCache.insights.collectAsState()
    val isGeneratingInsights by viewModel.isGeneratingInsights.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val attentionItems by viewModel.attentionItems.collectAsState()
    val context = LocalContext.current
    val askNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(attentionItems.isNotEmpty()) {
        if (attentionItems.isNotEmpty() && AttentionMonitor.shouldAskPermission(context)) {
            AttentionMonitor.markPermissionAsked()
            askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Finance Tracker")
                        if (lastSyncedAt != null) {
                            Text(
                                text = "Synced ${Formatters.syncTimestamp(lastSyncedAt!!)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(onClick = onOpenTrends) {
                        Icon(Icons.Filled.ShowChart, contentDescription = "Trends")
                    }
                    if (AiService.isConfigured) {
                        IconButton(onClick = onOpenAskAi) {
                            Icon(Icons.Filled.Chat, contentDescription = "Ask your finances")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PeriodSelectorChip(
                        option = state.periodOption,
                        customRange = state.customRange,
                        onOptionSelected = viewModel::selectPeriod,
                        onCustomRangeSelected = viewModel::selectCustomRange,
                        options = PeriodOption.entries
                    )
                    AccountSelectorChip(
                        accounts = state.accounts,
                        selectedAccountId = state.selectedAccountId,
                        onAccountSelected = viewModel::selectAccount
                    )
                }
            }
            item {
                SummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Net Balance",
                    amount = Formatters.currency(state.netBalance, currencyCode),
                    onClick = {
                        onOpenTransactions(
                            null, null, "All Transactions", state.periodOption, state.customRange, false, state.selectedAccountId, false
                        )
                    }
                )
            }
            item {
                val remaining = state.periodIncome - state.periodExpense
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Income",
                        amount = Formatters.currency(state.periodIncome, currencyCode),
                        valueColor = IncomeGreen,
                        onClick = {
                            onOpenTransactions(
                                TransactionType.INCOME,
                                null,
                                "Income",
                                state.periodOption,
                                state.customRange,
                                false,
                                state.selectedAccountId,
                                false
                            )
                        },
                        amountStyle = MaterialTheme.typography.titleSmall,
                        contentPadding = 12.dp
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Expenses",
                        amount = Formatters.currency(state.periodExpense, currencyCode),
                        valueColor = ExpenseRed,
                        onClick = {
                            onOpenTransactions(
                                TransactionType.EXPENSE,
                                null,
                                "Expenses",
                                state.periodOption,
                                state.customRange,
                                true,
                                state.selectedAccountId,
                                false
                            )
                        },
                        amountStyle = MaterialTheme.typography.titleSmall,
                        contentPadding = 12.dp,
                        titleBadge = "+".takeIf { state.anticipateRecurringBillsEnabled }
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Remaining",
                        amount = Formatters.currency(remaining, currencyCode),
                        valueColor = if (remaining >= 0) IncomeGreen else ExpenseRed,
                        amountStyle = MaterialTheme.typography.titleSmall,
                        contentPadding = 12.dp
                    )
                }
            }
            val budgetStatus = state.budgetStatus
            if (budgetStatus.overallBudget != null || budgetStatus.categoryStatuses.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Budget · This month", style = MaterialTheme.typography.titleMedium)
                            budgetStatus.overallBudget?.let { overallBudget ->
                                BudgetProgressRow(
                                    label = "Overall",
                                    spent = budgetStatus.overallSpent,
                                    budget = overallBudget,
                                    currencyCode = currencyCode,
                                    modifier = Modifier.padding(top = 8.dp),
                                    onClick = {
                                        onOpenTransactions(
                                            TransactionType.EXPENSE,
                                            null,
                                            "Overall Budget",
                                            PeriodOption.THIS_MONTH,
                                            null,
                                            false,
                                            state.selectedAccountId,
                                            false
                                        )
                                    }
                                )
                            }
                            budgetStatus.categoryStatuses.forEach { catStatus ->
                                BudgetProgressRow(
                                    label = catStatus.categoryName,
                                    spent = catStatus.spent,
                                    budget = catStatus.budget,
                                    currencyCode = currencyCode,
                                    colorHex = catStatus.colorHex,
                                    onClick = {
                                        onOpenTransactions(
                                            TransactionType.EXPENSE,
                                            catStatus.categoryId,
                                            catStatus.categoryName,
                                            PeriodOption.THIS_MONTH,
                                            null,
                                            false,
                                            state.selectedAccountId,
                                            false
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (state.uncategorizedCount > 0) {
                item {
                    SummaryCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Uncategorized",
                        amount = "${state.uncategorizedCount} transaction${if (state.uncategorizedCount == 1) "" else "s"}",
                        valueColor = ExpenseRed,
                        amountStyle = MaterialTheme.typography.titleMedium,
                        onClick = {
                            onOpenTransactions(
                                null,
                                null,
                                "Uncategorized",
                                state.periodOption,
                                state.customRange,
                                false,
                                state.selectedAccountId,
                                true
                            )
                        }
                    )
                }
            }
            if (attentionItems.isNotEmpty()) {
                item {
                    NeedsAttentionCard(
                        items = attentionItems,
                        canAsk = AiService.isConfigured,
                        onAsk = { prompt ->
                            AdvisorLaunch.ask(prompt)
                            onOpenAskAi()
                        },
                        onSeeTransactions = { filter ->
                            onOpenTransactions(
                                filter.type,
                                filter.categoryId,
                                filter.label,
                                if (filter.thisMonthOnly) PeriodOption.THIS_MONTH else PeriodOption.ALL_TIME,
                                null,
                                false,
                                filter.accountId,
                                filter.uncategorizedOnly
                            )
                        },
                        onDismiss = viewModel::dismissAttention
                    )
                }
            }
            if (AiService.isConfigured) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                    Text(
                                        text = "AI Insights",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                if (isGeneratingInsights) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Row {
                                        TextButton(onClick = viewModel::generateInsights) {
                                            Text(if (insights.isEmpty()) "Generate" else "Regenerate")
                                        }
                                        if (insights.isNotEmpty()) {
                                            IconButton(onClick = viewModel::dismissInsights) {
                                                Icon(Icons.Filled.Close, contentDescription = "Dismiss insights")
                                            }
                                        }
                                    }
                                }
                            }
                            if (insights.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    insights.forEach { card -> InsightCardItem(card) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(text = "Spending by category", style = MaterialTheme.typography.titleMedium)
            }
            item {
                if (state.categoryBreakdown.isEmpty()) {
                    EmptyState(message = "No expenses recorded for this period.")
                } else {
                    Card {
                        CategoryBreakdownList(
                            categories = state.categoryBreakdown,
                            currencyCode = currencyCode,
                            onCategoryClick = { spend ->
                                onOpenTransactions(
                                    TransactionType.EXPENSE,
                                    spend.categoryId,
                                    spend.categoryName,
                                    state.periodOption,
                                    state.customRange,
                                    false,
                                    state.selectedAccountId,
                                    false
                                )
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A single AI-generated insight, styled as a compact stat card: a tone-colored accent bar, a
 * muted label, the headline figure in a large tone-colored weight, and one line of context. */
@Composable
private fun InsightCardItem(card: InsightCard) {
    val accentColor = when (card.tone) {
        InsightTone.POSITIVE -> IncomeGreen
        InsightTone.WARNING -> ExpenseRed
        InsightTone.NEUTRAL -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = card.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = card.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = card.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** "Needs your attention": what the app's checks found (see AttentionChecks), most important
 * first. Only shown when there's something; each item can be opened, taken to the advisor or
 * dismissed. */
@Composable
private fun NeedsAttentionCard(
    items: List<AttentionItem>,
    canAsk: Boolean,
    onAsk: (String) -> Unit,
    onSeeTransactions: (AttentionFilter) -> Unit,
    onDismiss: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Text(
                    text = "Needs your attention",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                Text("${items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val visible = if (expanded) items else items.take(3)
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { item ->
                    AttentionRow(item, canAsk, onAsk, onSeeTransactions, onDismiss)
                }
            }
            if (items.size > 3) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show less" else "Show all ${items.size}")
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(
    item: AttentionItem,
    canAsk: Boolean,
    onAsk: (String) -> Unit,
    onSeeTransactions: (AttentionFilter) -> Unit,
    onDismiss: (String) -> Unit
) {
    val accent = when (item.level) {
        AttentionLevel.URGENT, AttentionLevel.WARNING -> ExpenseRed
        AttentionLevel.GOOD -> IncomeGreen
        AttentionLevel.INFO -> MaterialTheme.colorScheme.primary
    }
    val icon = when (item.level) {
        AttentionLevel.URGENT -> Icons.Filled.Error
        AttentionLevel.WARNING -> Icons.Filled.Warning
        AttentionLevel.GOOD -> Icons.Filled.CheckCircle
        AttentionLevel.INFO -> Icons.Filled.Info
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Text(
                item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row {
                item.filter?.let { filter ->
                    TextButton(onClick = { onSeeTransactions(filter) }) { Text("See transactions") }
                }
                if (canAsk && item.prompt != null) {
                    TextButton(onClick = { onAsk(item.prompt) }) { Text(item.promptLabel) }
                }
            }
        }
        IconButton(onClick = { onDismiss(item.key) }) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
        }
    }
}
