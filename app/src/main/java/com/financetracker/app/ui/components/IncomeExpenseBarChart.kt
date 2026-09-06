package com.financetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financetracker.app.ui.theme.ChartExpenseDark
import com.financetracker.app.ui.theme.ChartExpenseLight
import com.financetracker.app.ui.theme.ChartIncomeDark
import com.financetracker.app.ui.theme.ChartIncomeLight

data class BarChartEntry(
    val key: String,
    val label: String,
    val income: Double,
    val expense: Double
)

private const val CHART_HEIGHT_DP = 180
private const val BAR_WIDTH_DP = 14
private const val COLUMN_WIDTH_DP = 64

/**
 * A grouped (income vs. expense) bar chart on one shared axis — never dual-axis.
 * Tapping a category's bars selects it (tap again to clear); [onSelect] receives
 * the entry's key, or null when cleared.
 */
@Composable
fun IncomeExpenseBarChart(
    entries: List<BarChartEntry>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val incomeColor = if (isDark) ChartIncomeDark else ChartIncomeLight
    val expenseColor = if (isDark) ChartExpenseDark else ChartExpenseLight
    val maxValue = entries.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > 0 } ?: 1.0

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(incomeColor)
            Text(
                "Income",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp, end = 16.dp)
            )
            LegendDot(expenseColor)
            Text("Expense", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.forEach { entry ->
                val isSelected = entry.key == selectedKey
                Column(
                    modifier = Modifier
                        .width(COLUMN_WIDTH_DP.dp)
                        .clickable { onSelect(if (isSelected) null else entry.key) }
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.height(CHART_HEIGHT_DP.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Bar(
                            value = entry.income,
                            maxValue = maxValue,
                            color = incomeColor
                        )
                        Bar(
                            value = entry.expense,
                            maxValue = maxValue,
                            color = expenseColor
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(COLUMN_WIDTH_DP.dp)
                    )
                }
            }
        }
        if (entries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scale: up to ${formatValue(maxValue)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Bar(value: Double, maxValue: Double, color: Color) {
    val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(BAR_WIDTH_DP.dp)
            .fillMaxHeight(if (fraction > 0f) fraction else 0.01f)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color)
    )
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}
