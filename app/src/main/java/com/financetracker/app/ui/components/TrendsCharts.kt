package com.financetracker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financetracker.app.ui.screens.trends.MonthlyTrend
import com.financetracker.app.ui.theme.ExpenseRed
import com.financetracker.app.ui.theme.IncomeGreen
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * A shared-axis line chart of income (solid) vs. expense (dashed — a non-color cue, since
 * the two hues alone sit right at the colorblind-safety floor) across [trends], with each
 * line's latest value printed directly at its end.
 */
@Composable
fun IncomeExpenseTrendChart(
    trends: List<MonthlyTrend>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val labelStyle = TextStyle(fontSize = 9.sp, color = axisColor)
    val incomeLabelStyle = TextStyle(fontSize = 10.sp, color = IncomeGreen)
    val expenseLabelStyle = TextStyle(fontSize = 10.sp, color = ExpenseRed)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val padL = 38.dp.toPx()
        val padR = 10.dp.toPx()
        val padT = 26.dp.toPx()
        val padB = 22.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        val n = trends.size

        val axisMax = niceAxisMax(trends.maxOf { max(it.income, it.expense) })

        fun xAt(i: Int): Float = padL + if (n > 1) plotW * i / (n - 1) else plotW / 2f
        fun yAt(v: Double): Float = padT + plotH - (plotH * (v / axisMax)).toFloat()

        val steps = 4
        for (s in 0..steps) {
            val value = axisMax * s / steps
            val gy = yAt(value)
            drawLine(gridColor, Offset(padL, gy), Offset(size.width - padR, gy), strokeWidth = 1f)
            val text = formatAxisValue(value)
            val measured = textMeasurer.measure(text, labelStyle)
            drawText(
                textMeasurer,
                text,
                topLeft = Offset(padL - 6f - measured.size.width, gy - measured.size.height / 2f),
                style = labelStyle
            )
        }

        trends.forEachIndexed { i, t ->
            val measured = textMeasurer.measure(t.monthLabel, labelStyle)
            drawText(
                textMeasurer,
                t.monthLabel,
                topLeft = Offset(xAt(i) - measured.size.width / 2f, size.height - padB + 6f),
                style = labelStyle
            )
        }

        fun linePath(values: List<Double>): Path = Path().apply {
            values.forEachIndexed { i, v ->
                val px = xAt(i)
                val py = yAt(v)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        }
        fun areaPath(values: List<Double>): Path = Path().apply {
            values.forEachIndexed { i, v ->
                val px = xAt(i)
                val py = yAt(v)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            lineTo(xAt(n - 1), yAt(0.0))
            lineTo(xAt(0), yAt(0.0))
            close()
        }

        val incomeValues = trends.map { it.income }
        val expenseValues = trends.map { it.expense }

        drawPath(areaPath(incomeValues), color = IncomeGreen.copy(alpha = 0.12f))
        drawPath(areaPath(expenseValues), color = ExpenseRed.copy(alpha = 0.12f))

        drawPath(
            linePath(incomeValues),
            color = IncomeGreen,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            linePath(expenseValues),
            color = ExpenseRed,
            style = Stroke(
                width = 5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 14f))
            )
        )

        incomeValues.forEachIndexed { i, v ->
            val center = Offset(xAt(i), yAt(v))
            drawCircle(surfaceColor, radius = 6f, center = center)
            drawCircle(IncomeGreen, radius = 6f, center = center, style = Stroke(width = 4f))
        }
        expenseValues.forEachIndexed { i, v ->
            val center = Offset(xAt(i), yAt(v))
            drawCircle(surfaceColor, radius = 6f, center = center)
            drawCircle(ExpenseRed, radius = 6f, center = center, style = Stroke(width = 4f))
        }

        val lastI = n - 1
        val incomeLabel = "Income  ${formatValue(incomeValues[lastI])}"
        val expenseLabel = "Expense  ${formatValue(expenseValues[lastI])}"
        val incomeMeasured = textMeasurer.measure(incomeLabel, incomeLabelStyle)
        val expenseMeasured = textMeasurer.measure(expenseLabel, expenseLabelStyle)
        drawText(
            textMeasurer,
            incomeLabel,
            topLeft = Offset(
                size.width - padR - incomeMeasured.size.width,
                yAt(incomeValues[lastI]) - incomeMeasured.size.height - 8f
            ),
            style = incomeLabelStyle
        )
        drawText(
            textMeasurer,
            expenseLabel,
            topLeft = Offset(size.width - padR - expenseMeasured.size.width, yAt(expenseValues[lastI]) + 8f),
            style = expenseLabelStyle
        )
    }
}

/** A zero-baseline bar per month, colored by the sign of income − expense. */
@Composable
fun NetTrendChart(trends: List<MonthlyTrend>, modifier: Modifier = Modifier) {
    if (trends.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 9.sp, color = axisColor)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        val padL = 38.dp.toPx()
        val padR = 10.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 20.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        val n = trends.size

        val axisMax = niceAxisMax(trends.maxOf { abs(it.net) }.coerceAtLeast(1.0))
        val zeroY = padT + plotH / 2f

        fun xCenter(i: Int): Float = padL + plotW * (i + 0.5f) / n
        fun barHeight(v: Double): Float = (plotH / 2f * (abs(v) / axisMax)).toFloat()

        drawLine(gridColor, Offset(padL, zeroY), Offset(size.width - padR, zeroY), strokeWidth = 1f)
        val zeroMeasured = textMeasurer.measure("0", labelStyle)
        drawText(
            textMeasurer,
            "0",
            topLeft = Offset(padL - 6f - zeroMeasured.size.width, zeroY - zeroMeasured.size.height / 2f),
            style = labelStyle
        )

        val barW = (plotW / n) * 0.46f
        trends.forEachIndexed { i, t ->
            val h = barHeight(t.net).coerceAtLeast(3f)
            val color = if (t.net >= 0) IncomeGreen else ExpenseRed
            val top = if (t.net >= 0) zeroY - h else zeroY
            drawRoundRect(
                color = color,
                topLeft = Offset(xCenter(i) - barW / 2f, top),
                size = Size(barW, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
            val measured = textMeasurer.measure(t.monthLabel, labelStyle)
            drawText(
                textMeasurer,
                t.monthLabel,
                topLeft = Offset(xCenter(i) - measured.size.width / 2f, size.height - padB + 6f),
                style = labelStyle
            )
        }
    }
}

private fun niceAxisMax(value: Double): Double {
    if (value <= 0.0) return 100.0
    val magnitude = 10.0.pow(floor(ln(value) / ln(10.0)))
    val residual = value / magnitude
    val niceResidual = when {
        residual <= 1.0 -> 1.0
        residual <= 2.0 -> 2.0
        residual <= 5.0 -> 5.0
        else -> 10.0
    }
    return niceResidual * magnitude
}

private fun formatAxisValue(value: Double): String {
    val thousands = value / 1000.0
    return if (thousands == floor(thousands)) {
        "${thousands.toInt()}k"
    } else {
        "%.1fk".format(thousands)
    }
}
