package com.financetracker.app.data.stores

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * How the app signs in to a store and fetches its receipts. Signing in always happens on the
 * store's own login page in a window in the app: the person types their e-mail and password
 * there, and the app only picks up what the store hands back afterwards (a sign-in code, or the
 * store's login cookie), never the password.
 */
interface StoreConnector {
    val store: Store

    /** The store's login page to open. */
    fun loginUrl(): String

    /** Called for every address the login window is about to open. Returns what the store handed
     * back when this is the end of the login (e.g. the sign-in code), else null. */
    fun captureFromUrl(url: String): String? = null

    /** The site whose cookies [captureFromCookies] looks at, or null. */
    val cookieUrl: String? get() = null

    /** Called after each page has loaded, with the cookies the login window has for the store.
     * Returns them when they show the person is signed in, else null. */
    fun captureFromCookies(cookies: String?, userAgent: String): String? = null

    /** Turns what was captured into the account's stored sign-in. */
    suspend fun finishLogin(captured: String): String

    /** Receipts from [since] on. Returns them and the sign-in to keep (it may have been renewed).
     * Throws [StoreLoginExpired] when the person needs to sign in again. */
    suspend fun fetch(secret: String, since: Long): Pair<List<StoreReceipt>, String>

    companion object {
        fun forStore(store: Store): StoreConnector? = when (store) {
            Store.LIDL -> LidlPlus()
            Store.NEMLIG -> Nemlig()
            Store.COOP -> null
        }
    }
}

/** Helpers for reading the stores' JSON, which writes amounts as numbers or as "12,95". */
internal object StoreJson {
    fun number(o: JSONObject?, key: String): Double? {
        if (o == null || !o.has(key) || o.isNull(key)) return null
        val v = o.opt(key)
        if (v is Number) return v.toDouble()
        val s = v.toString().trim().replace(Regex("[^0-9,.-]"), "")
        if (s.isEmpty()) return null
        val normalised = when {
            s.contains(',') && s.contains('.') -> if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            s.contains(',') -> s.replace(',', '.')
            else -> s
        }
        return normalised.toDoubleOrNull()
    }

    fun text(o: JSONObject?, key: String): String? =
        o?.optString(key)?.trim()?.takeUnless { o.isNull(key) || it.isEmpty() || it == "null" }

    /** ISO 8601 date-time ("2026-10-05T14:23:00", with or without seconds, fraction or zone). */
    fun time(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val v = value.trim().replace(Regex("\\.\\d+"), "")
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")
        for (p in patterns) {
            val format = SimpleDateFormat(p, Locale.US).apply {
                isLenient = false
                if (p.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC") else timeZone = TimeZone.getTimeZone("Europe/Copenhagen")
            }
            runCatching { return format.parse(v)!!.time }
        }
        return null
    }
}
