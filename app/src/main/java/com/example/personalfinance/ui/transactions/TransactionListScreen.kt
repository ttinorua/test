package com.example.personalfinance.ui.transactions

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import com.example.personalfinance.data.TransactionType
import com.example.personalfinance.data.TransactionWithCategory
import com.example.personalfinance.ui.common.TransactionRow

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onTransactionClick: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<TransactionWithCategory?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.filter.type == null,
                        onClick = { viewModel.setTypeFilter(null) },
                        label = { Text("All") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.filter.type == TransactionType.INCOME,
                        onClick = { viewModel.setTypeFilter(TransactionType.INCOME) },
                        label = { Text("Income") }
                    )
                }
                item {
                    FilterChip(
                        selected = state.filter.type == TransactionType.EXPENSE,
                        onClick = { viewModel.setTypeFilter(TransactionType.EXPENSE) },
                        label = { Text("Expense") }
                    )
                }
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.filter.categoryId == category.id,
                        onClick = {
                            viewModel.setCategoryFilter(
                                if (state.filter.categoryId == category.id) null else category.id
                            )
                        },
                        label = { Text(category.name) }
                    )
                }
            }
        }

        if (state.groups.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No transactions match this filter.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        state.groups.forEach { group ->
            item {
                Text(
                    text = group.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(group.items, key = { it.transaction.id }) { item ->
                TransactionRow(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onTransactionClick(item.transaction.id) },
                            onLongClick = { pendingDelete = item }
                        )
                )
            }
            item { HorizontalDivider() }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete transaction?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(item)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
