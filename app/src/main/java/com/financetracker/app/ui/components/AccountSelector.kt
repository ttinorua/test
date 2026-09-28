package com.financetracker.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.financetracker.app.data.db.entity.Account

/** Narrows a screen's data to one account, or all of them (null) when nothing is selected. */
@Composable
fun AccountSelectorChip(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onAccountSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val label = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "All accounts"

    Box(modifier = modifier) {
        AssistChip(
            onClick = { menuExpanded = true },
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) }
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("All accounts") },
                onClick = {
                    menuExpanded = false
                    onAccountSelected(null)
                }
            )
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        menuExpanded = false
                        onAccountSelected(account.id)
                    }
                )
            }
        }
    }
}
