package com.financetracker.app.ui.theme

import androidx.compose.ui.graphics.Color

val GreenPrimary = Color(0xFF2E7D32)
val GreenPrimaryDark = Color(0xFFA5D6A7)
val GreenSecondary = Color(0xFF00695C)
val ExpenseRed = Color(0xFFD32F2F)
val IncomeGreen = Color(0xFF2E7D32)

val LightBackground = Color(0xFFFAFAF7)
val LightSurface = Color(0xFFFFFFFF)
val DarkBackground = Color(0xFF121412)
val DarkSurface = Color(0xFF1B1F1B)

// Validated colorblind-safe pair for the income/expense dual bar chart
// (CVD delta-E and normal-vision delta-E both clear the safety floor).
val ChartIncomeLight = Color(0xFF2A78D6)
val ChartIncomeDark = Color(0xFF3987E5)
val ChartExpenseLight = Color(0xFFEB6834)
val ChartExpenseDark = Color(0xFFD95926)

// Budget progress "nearing limit" state (under = default onSurface, over = ExpenseRed).
val BudgetWarningLight = Color(0xFFB25E00)
val BudgetWarningDark = Color(0xFFFFB74D)
