package com.example.personalfinance.ui.common

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
    runCatching { currency = Currency.getInstance(Locale.getDefault()) }
}

fun formatAmount(amount: Double): String = currencyFormat.format(amount)

private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

fun formatDay(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dayFormatter)

fun formatMonth(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(monthFormatter)
