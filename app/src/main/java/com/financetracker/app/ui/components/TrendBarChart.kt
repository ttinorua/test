package com.financetracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financetracker.app.util.Formatters

/** One colored series of a [TrendBarChart], e.g. Expense, with one value per bar group. */
data class TrendSeries(val name: String, val color: Color, val values: List<Double>)

private val PLOT_HEIGHT = 150.dp
private val LABEL_SPACE = 16.dp
private val MIN_GROUP_WIDTH_SINGLE = 40.dp
private val MIN_GROUP_WIDTH_MULTI = 52.dp

/**
 * Compact bars over time — one group per month or year, oldest on the left. Groups share the
 * available width when there are few of them; with many they keep a compact minimum width and
 * the chart scrolls horizontally, starting scrolled to the newest. Each bar shows a short value
 * above it, and tapping a bar reports its group and series.
 */
@Composable
fun TrendBarChart(
    labels: List<String>,
    subLabels: List<String?>,
    series: List<TrendSeries>,
    onBarClick: (index: Int, seriesIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = labels.size
    if (count == 0 || series.isEmpty()) return
    val maxValue = series.flatMap { it.values }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val scrollState = rememberScrollState()
    LaunchedEffect(count) { scrollState.scrollTo(scrollState.maxValue) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minGroup = if (series.size > 1) MIN_GROUP_WIDTH_MULTI else MIN_GROUP_WIDTH_SINGLE
        val groupWidth = maxOf(minGroup, maxWidth / count)
        val barWidth = if (series.size > 1) 16.dp else minOf(24.dp, groupWidth - 12.dp)
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            for (index in 0 until count) {
                Column(
                    modifier = Modifier.width(groupWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.height(PLOT_HEIGHT + LABEL_SPACE),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        series.forEachIndexed { seriesIndex, s ->
                            TrendBar(
                                value = s.values.getOrElse(index) { 0.0 },
                                maxValue = maxValue,
                                color = s.color,
                                width = barWidth,
                                groupWidth = groupWidth,
                                onClick = { onBarClick(index, seriesIndex) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(groupWidth)
                            .clickable { onBarClick(index, -1) }
                    )
                    Text(
                        text = subLabels.getOrNull(index) ?: "",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(groupWidth)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendBar(
    value: Double,
    maxValue: Double,
    color: Color,
    width: Dp,
    groupWidth: Dp,
    onClick: () -> Unit
) {
    val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
    val barHeight = if (value > 0) maxOf(PLOT_HEIGHT * fraction, 2.dp) else 0.dp
    Box(
        modifier = Modifier
            .width(width)
            .height(PLOT_HEIGHT + LABEL_SPACE)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(color)
        )
        if (value > 0) {
            // Wider than the bar itself so the label isn't clipped; it's centered on the bar.
            Text(
                text = Formatters.compact(value),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .requiredWidth(groupWidth)
                    .offset(y = -(barHeight + 2.dp))
            )
        }
    }
}
