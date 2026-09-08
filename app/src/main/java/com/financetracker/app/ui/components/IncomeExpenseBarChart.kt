package com.financetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import kotlin.math.abs

data class BarChartEntry(
    val key: String,
    val label: String,
    val income: Double,
    val expense: Double
)

private const val CHART_HEIGHT_DP = 160
private const val BAR_WIDTH_DP = 28
private const val COLUMN_WIDTH_DP = 48

/**
 * One bar per entry, showing its net (income − expense): green above the axis,
 * red below. Columns sit close together (no per-series legend, single value each)
 * so many entries stay scannable on one row. Tapping a bar selects it.
 */
@Composable
fun IncomeExpenseBarChart(
    entries: List<BarChartEntry>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val maxAbsNet = entries.maxOfOrNull { abs(it.income - it.expense) }?.takeIf { it > 0 } ?: 1.0

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            entries.forEach { entry ->
                val net = entry.income - entry.expense
                val isSelected = entry.key == selectedKey
                val color = if (net >= 0) IncomeGreen else ExpenseRed
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
                    Box(
                        modifier = Modifier.height(CHART_HEIGHT_DP.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Bar(value = abs(net), maxValue = maxAbsNet, color = color)
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scale: up to ${formatValue(maxAbsNet)} (green = net income, red = net spend)",
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
