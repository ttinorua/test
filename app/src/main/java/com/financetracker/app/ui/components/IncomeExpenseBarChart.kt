package com.financetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.sp
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen

data class BarChartEntry(
    val key: String,
    val label: String,
    val income: Double,
    val expense: Double
)

private const val CHART_HEIGHT_DP = 160
private const val BAR_WIDTH_DP = 16
private const val COLUMN_WIDTH_DP = 72
private const val MIN_LABEL_SPACE_DP = 20

/**
 * A grouped (income vs. expense) bar chart on one shared axis, with each bar's own
 * value printed above it — or, if the bar is tall enough that a label above it would
 * be clipped, inside the bar near its top instead. Tapping a bar's column selects it.
 */
@Composable
fun IncomeExpenseBarChart(
    entries: List<BarChartEntry>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val maxValue = entries.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > 0 } ?: 1.0

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(IncomeGreen)
            Text(
                "Income",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp, end = 16.dp)
            )
            LegendDot(ExpenseRed)
            Text("Expense", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        modifier = Modifier
                            .height(CHART_HEIGHT_DP.dp)
                            .width(COLUMN_WIDTH_DP.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                    ) {
                        // A category/main-category entry is inherently one type or the other
                        // (a category never mixes income and expense), so only draw the bar
                        // that's actually non-zero — an account, which does mix both, gets both.
                        if (entry.income > 0) {
                            Bar(value = entry.income, maxValue = maxValue, color = IncomeGreen, formatValue = formatValue)
                        }
                        if (entry.expense > 0) {
                            Bar(value = entry.expense, maxValue = maxValue, color = ExpenseRed, formatValue = formatValue)
                        }
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
    }
}

/** Only ever called with a positive [value] — zero-value bars are simply omitted by the caller. */
@Composable
private fun Bar(value: Double, maxValue: Double, color: Color, formatValue: (Double) -> String) {
    val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
    val barHeightDp = maxOf((CHART_HEIGHT_DP * fraction).dp, 2.dp)
    val labelInside = (CHART_HEIGHT_DP.dp - barHeightDp) < MIN_LABEL_SPACE_DP.dp

    Box(
        modifier = Modifier
            .width(BAR_WIDTH_DP.dp)
            .height(CHART_HEIGHT_DP.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeightDp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(color)
        )
        Text(
            text = formatValue(value),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            color = if (labelInside) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = if (labelInside) -(barHeightDp - 6.dp) else -(barHeightDp + 4.dp))
        )
    }
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
