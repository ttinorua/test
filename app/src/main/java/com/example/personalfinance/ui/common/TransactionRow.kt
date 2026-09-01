package com.example.personalfinance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalfinance.data.TransactionType
import com.example.personalfinance.data.TransactionWithCategory
import com.example.personalfinance.ui.theme.ExpenseRed
import com.example.personalfinance.ui.theme.IncomeGreen

@Composable
fun TransactionRow(
    item: TransactionWithCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val category = item.category
    val categoryColor = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(categoryColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CategoryIcons.forKey(category?.icon ?: "other"),
                contentDescription = null,
                tint = categoryColor
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category?.name ?: "Uncategorized",
                style = MaterialTheme.typography.bodyLarge
            )
            if (item.transaction.note.isNotBlank()) {
                Text(
                    text = item.transaction.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        val isIncome = item.transaction.type == TransactionType.INCOME
        Text(
            text = (if (isIncome) "+" else "-") + formatAmount(item.transaction.amount),
            color = if (isIncome) IncomeGreen else ExpenseRed,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
