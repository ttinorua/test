package com.financetracker.app.data.enablebanking

import com.financetracker.app.data.db.entity.TransactionType
import com.financetracker.app.data.importexport.ParsedTransactionRow
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.prefs.LinkedBankAccount
import com.financetracker.app.util.todayUtcMidnight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
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

    /** Stand-in for "no lower bound" when fetching transactions, since the API needs an
     * explicit date_from to actually return full history (see fetchTransactions()). */
    private val FULL_HISTORY_SINCE_MILLIS: Long by lazy { parseDateOnly("1990-01-01") ?: 0L }

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
                    val iban = accountObj.optJSONObject("account_id")?.stringOrNull("iban")
                    LinkedBankAccount(
                        uid = accountObj.getString("uid"),
                        iban = iban,
                        name = accountObj.stringOrNull("name") ?: iban ?: "Account",
                        product = accountObj.stringOrNull("product"),
                        currency = accountObj.stringOrNull("currency") ?: "DKK"
                    )
                }
                val consentValidUntil = json.optJSONObject("access")?.stringOrNull("valid_until")
                    ?.let { parseRfc3339ToEpochMillis(it) }
                    ?: (System.currentTimeMillis() + CONSENT_VALIDITY_DAYS * 24 * 60 * 60 * 1000)
                EnableBankingPrefs.saveConnection(sessionId, accounts, consentValidUntil)
                Result.success(accounts)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    /** Fetches every transaction booked on or after [sinceEpochMillis] (or the account's full
     * history if null) for one linked account, mapped into the same row shape spreadsheet
     * import uses so the existing duplicate-detection and insert pipeline can be reused as-is.
     *
     * Confirmed live against Sydbank: omitting date_from entirely does NOT return full history —
     * it silently defaults to a recent window (observed: only the last ~90 days). Passing an
     * explicit old date does return everything the bank has (observed: back to 2015 for a real
     * account), so "all history" is implemented as an explicit far-past date_from, never as
     * leaving the parameter out. */
    /** [onPage] is called after each page is fetched (with the running row count so far) so a
     * caller can show that a long paginated fetch — the full history of a years-old account can
     * be dozens of pages, each its own network round trip, done before this function returns
     * anything at all — is actually progressing, not stuck. */
    suspend fun fetchTransactions(
        accountUid: String,
        sinceEpochMillis: Long?,
        onPage: suspend (Int) -> Unit = {}
    ): Result<List<ParsedTransactionRow>> =
        withContext(Dispatchers.IO) {
            try {
                val dateFromParam = "?date_from=${formatDate(Date(sinceEpochMillis ?: FULL_HISTORY_SINCE_MILLIS))}"
                val rows = mutableListOf<ParsedTransactionRow>()
                var continuationKey: String? = null
                var rowNumber = 0
                do {
                    val separator = if (dateFromParam.isEmpty()) "?" else "&"
                    val query = dateFromParam + (continuationKey?.let {
                        "${separator}continuation_key=${URLEncoder.encode(it, "UTF-8")}"
                    } ?: "")
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
                    onPage(rows.size)
                    // Enable Banking sends this key back as an explicit JSON null (not an
                    // omitted field) once there's no more data — see stringOrNull() below for
                    // why that has to be handled explicitly rather than via optString() alone.
                    continuationKey = json.stringOrNull("continuation_key")
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
        // Sydbank shows scheduled/standing transfers ahead of their real date, so without this
        // filter a future-dated "PDNG" entry can (a) get imported as if it already happened and
        // (b) get picked by BalanceReconciler as the latest row, skewing the reconciled balance
        // with a transaction that hasn't actually settled yet.
        if (obj.stringOrNull("status") == "PDNG") return null

        val bookingDate = obj.stringOrNull("booking_date") ?: obj.stringOrNull("value_date") ?: return null
        val date = parseDateOnly(bookingDate) ?: return null
        if (date > todayUtcMidnight()) return null

        val amountObj = obj.optJSONObject("transaction_amount") ?: return null
        val amount = amountObj.stringOrNull("amount")?.toDoubleOrNull()?.let { Math.abs(it) } ?: return null

        val type = if (obj.stringOrNull("credit_debit_indicator") == "CRDT") {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }

        val remittanceArray = obj.optJSONArray("remittance_information")
        val remittance = remittanceArray?.let { array ->
            (0 until array.length()).mapNotNull { i -> if (array.isNull(i)) null else array.optString(i) }
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }.orEmpty()
        val counterpartyName = obj.optJSONObject("creditor")?.stringOrNull("name")
            ?: obj.optJSONObject("debtor")?.stringOrNull("name")
        val note = remittance.ifBlank { counterpartyName.orEmpty() }

        val balanceAfter = obj.optJSONObject("balance_after_transaction")?.stringOrNull("amount")?.toDoubleOrNull()

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
            JSONObject(response.body).stringOrNull("message") ?: response.body
        } catch (e: Exception) {
            response.body
        }
        return "Enable Banking request failed (${response.status}): $detail"
    }

    private fun mapError(t: Throwable): Throwable = when (t) {
        is EnableBankingNotConfiguredException, is EnableBankingRequestException -> t
        else -> EnableBankingRequestException("Enable Banking request failed: ${t.message}", t)
    }

    /** org.json's optString() doesn't treat a JSON null as absent — it stringifies it to the
     * literal text "null" instead. Every field read from this API is optional in principle, so
     * every string read goes through this rather than optString() directly. */
    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

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
