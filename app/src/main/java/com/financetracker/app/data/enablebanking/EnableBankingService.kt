package com.financetracker.app.data.enablebanking

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.ParsedTransactionRow
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.prefs.LinkedBankAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class EnableBankingRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class AuthStart(val url: String, val state: String)

/**
 * Talks to Enable Banking's open-banking (PSD2/AISP) API to link and sync a Sydbank account.
 * The app's application ID and private key ship inside BuildConfig (set via local.properties,
 * never committed) — the same deliberate tradeoff already made for the Claude API key, only
 * acceptable because this app is shared privately, never published. See EnableBankingJwt for
 * how requests are authorized (no client secret; every call carries a freshly-signed RS256 JWT).
 */
object EnableBankingService {

    private const val ASPSP_NAME = "Sydbank"
    private const val ASPSP_COUNTRY = "DK"
    private const val CONSENT_VALIDITY_DAYS = 180L

    val isConfigured: Boolean get() = EnableBankingJwt.isConfigured

    /** Starts a new consent flow: POSTs /auth and returns the URL to send the user to (their
     * bank's MitID login), plus the `state` value to verify once the redirect comes back. */
    suspend fun startAuth(redirectUrl: String): Result<AuthStart> = withContext(Dispatchers.IO) {
        try {
            val state = UUID.randomUUID().toString()
            val validUntil = Date(System.currentTimeMillis() + CONSENT_VALIDITY_DAYS * 24 * 60 * 60 * 1000)
            val body = JSONObject().apply {
                put(
                    "access",
                    JSONObject().apply {
                        put("valid_until", isoMicros(validUntil))
                        put("balances", true)
                        put("transactions", true)
                    }
                )
                put("aspsp", JSONObject().apply { put("name", ASPSP_NAME); put("country", ASPSP_COUNTRY) })
                put("state", state)
                put("redirect_url", redirectUrl)
                put("psu_type", "personal")
            }
            val response = EnableBankingApi.post("/auth", body)
            if (response.status !in 200..299) throw EnableBankingRequestException(apiErrorMessage(response))
            val json = JSONObject(response.body)
            EnableBankingPrefs.setPendingAuthState(state)
            Result.success(AuthStart(url = json.getString("url"), state = state))
        } catch (e: Exception) {
            Result.failure(mapError(e))
        }
    }

    /** Exchanges the authorization `code` from the redirect for a live session, verifies
     * `state` matches what we sent, and persists the resulting accounts. */
    suspend fun completeAuth(code: String, returnedState: String?): Result<List<LinkedBankAccount>> =
        withContext(Dispatchers.IO) {
            try {
                val expectedState = EnableBankingPrefs.consumePendingAuthState()
                if (expectedState == null || expectedState != returnedState) {
                    throw EnableBankingRequestException("Bank login response didn't match the request that started it.")
                }
                val response = EnableBankingApi.post("/sessions", JSONObject().apply { put("code", code) })
                if (response.status !in 200..299) throw EnableBankingRequestException(apiErrorMessage(response))
                val json = JSONObject(response.body)
                val sessionId = json.getString("session_id")
                val accountsJson = json.getJSONArray("accounts")
                val accounts = (0 until accountsJson.length()).map { i ->
                    val accountObj = accountsJson.getJSONObject(i)
                    val iban = accountObj.optJSONObject("account_id")?.let {
                        if (it.isNull("iban")) null else it.getString("iban")
                    }
                    LinkedBankAccount(
                        uid = accountObj.getString("uid"),
                        iban = iban,
                        name = accountObj.optString("name").ifBlank { iban ?: "Account" },
                        product = accountObj.optString("product").ifBlank { null },
                        currency = accountObj.optString("currency", "DKK")
                    )
                }
                val consentValidUntil = json.optJSONObject("access")?.optString("valid_until")
                    ?.let { parseRfc3339ToEpochMillis(it) }
                    ?: (System.currentTimeMillis() + CONSENT_VALIDITY_DAYS * 24 * 60 * 60 * 1000)
                EnableBankingPrefs.saveConnection(sessionId, accounts, consentValidUntil)
                Result.success(accounts)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    /** Fetches every transaction booked on or after [sinceEpochMillis] (or all history if
     * null) for one linked account, mapped into the same row shape spreadsheet import uses so
     * the existing duplicate-detection and insert pipeline can be reused as-is. */
    suspend fun fetchTransactions(accountUid: String, sinceEpochMillis: Long?): Result<List<ParsedTransactionRow>> =
        withContext(Dispatchers.IO) {
            try {
                val dateFromParam = sinceEpochMillis?.let { "?date_from=${formatDate(Date(it))}" }.orEmpty()
                val rows = mutableListOf<ParsedTransactionRow>()
                var continuationKey: String? = null
                var rowNumber = 0
                do {
                    val separator = if (dateFromParam.isEmpty()) "?" else "&"
                    val query = dateFromParam + (continuationKey?.let { "${separator}continuation_key=$it" } ?: "")
                    val response = EnableBankingApi.get("/accounts/$accountUid/transactions$query")
                    if (response.status !in 200..299) throw EnableBankingRequestException(apiErrorMessage(response))
                    val json = JSONObject(response.body)
                    val transactionsJson = json.optJSONArray("transactions")
                    if (transactionsJson != null) {
                        for (i in 0 until transactionsJson.length()) {
                            rowNumber++
                            mapTransaction(transactionsJson.getJSONObject(i), rowNumber)?.let { rows.add(it) }
                        }
                    }
                    continuationKey = json.optString("continuation_key").takeIf { it.isNotBlank() }
                } while (continuationKey != null)
                Result.success(rows)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    fun disconnect() {
        EnableBankingPrefs.disconnect()
    }

    private fun mapTransaction(obj: JSONObject, rowNumber: Int): ParsedTransactionRow? {
        val bookingDate = obj.optString("booking_date").takeIf { it.isNotBlank() }
            ?: obj.optString("value_date").takeIf { it.isNotBlank() }
            ?: return null
        val date = parseDateOnly(bookingDate) ?: return null

        val amountObj = obj.optJSONObject("transaction_amount") ?: return null
        val amount = amountObj.optString("amount").toDoubleOrNull()?.let { Math.abs(it) } ?: return null

        val type = if (obj.optString("credit_debit_indicator") == "CRDT") {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }

        val remittanceArray = obj.optJSONArray("remittance_information")
        val remittance = remittanceArray?.let { array ->
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.joinToString(" ")
        }.orEmpty()
        val counterpartyName = obj.optJSONObject("creditor")?.optString("name")
            ?: obj.optJSONObject("debtor")?.optString("name")
        val note = remittance.ifBlank { counterpartyName.orEmpty() }

        val balanceAfter = obj.optJSONObject("balance_after_transaction")?.optString("amount")?.toDoubleOrNull()

        return ParsedTransactionRow(
            rowNumber = rowNumber,
            date = date,
            note = note,
            mainCategoryName = "",
            categoryName = "",
            type = type,
            amount = amount,
            balanceAfter = balanceAfter
        )
    }

    private fun apiErrorMessage(response: EnableBankingApi.ApiResponse): String {
        val detail = try {
            JSONObject(response.body).optString("message").ifBlank { response.body }
        } catch (e: Exception) {
            response.body
        }
        return "Enable Banking request failed (${response.status}): $detail"
    }

    private fun mapError(t: Throwable): Throwable = when (t) {
        is EnableBankingNotConfiguredException, is EnableBankingRequestException -> t
        else -> EnableBankingRequestException("Enable Banking request failed: ${t.message}", t)
    }

    private fun isoMicros(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return "${fmt.format(date)}000+00:00"
    }

    private fun formatDate(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(date)
    }

    private fun parseDateOnly(value: String): Long? = try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(value)?.time
    } catch (e: Exception) {
        null
    }

    private fun parseRfc3339ToEpochMillis(value: String): Long? = try {
        val base = value.substringBefore(".")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(base)?.time
    } catch (e: Exception) {
        null
    }
}
