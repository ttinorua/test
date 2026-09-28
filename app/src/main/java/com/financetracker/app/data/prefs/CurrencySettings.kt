package com.financetracker.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Common currencies a personal-finance user might want; DKK first since it's the app default. */
val SUPPORTED_CURRENCIES = listOf("DKK", "USD", "EUR", "GBP", "NOK", "SEK")

/**
 * App-wide display currency. Purely cosmetic (no conversion) — every amount in the
 * database is just a plain number; this only controls how it's rendered.
 */
object CurrencySettings {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_CURRENCY_CODE = "currency_code"
    private const val DEFAULT_CURRENCY = "DKK"

    private lateinit var prefs: android.content.SharedPreferences
    private val _currencyCode = MutableStateFlow(DEFAULT_CURRENCY)
    val currencyCode: StateFlow<String> get() = _currencyCode

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _currencyCode.value = prefs.getString(KEY_CURRENCY_CODE, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
    }

    fun setCurrencyCode(code: String) {
        _currencyCode.value = code
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_CURRENCY_CODE, code).apply()
        }
    }
}
