package com.financetracker.app.data.stores

import android.net.Uri
import android.util.Base64
import com.financetracker.app.data.ai.ParsedReceipt
import com.financetracker.app.data.ai.ParsedReceiptItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.abs

/**
 * REMA 1000, through its Scan Selv app: only purchases made with Scan Selv have a digital receipt
 * (an ordinary checkout purchase isn't tied to the account). Login is REMA's own page with the
 * Scan Selv app's sign-in (OAuth with PKCE); the one-time code is swapped for tokens at the app's
 * backend, which also serves the receipts. Unofficial, from the owner's inspection of the Scan
 * Selv app and checks of its addresses; it can stop working if REMA changes it.
 */
class Rema1000 : StoreConnector {
    override val store = Store.REMA

    private val verifier: String = ByteArray(48).also { SecureRandom().nextBytes(it) }
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }

    override fun loginUrl(emailHint: String?): String {
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        // Start at REMA's authorize address, as the Scan Selv app does: it sets up the sign-in
        // session (a cookie) and forwards to the login page. Opening the login page directly
        // signs in but can't send the code back ("An error occurred while redirecting").
        return "$AUTH/api/oauth2/authorize?client_id=$CLIENT_ID&redirect_uri=${enc(REDIRECT)}&state=" +
            "&code_challenge=$challenge&code_challenge_method=S256&scope=&response_type=code" +
            "&theme=dark&lang=en&prompt=login&hide_login_terms=true&screen=login"
    }

    override fun captureFromUrl(url: String): String? {
        if (!url.startsWith(REDIRECT)) return null
        return Uri.parse(url).getQueryParameter("code")
    }

    /** How the code reaches token/new isn't confirmed (the request model shows only codeVerifier),
     * so the likely forms are tried in turn; a wrong form is turned down before the code is used. */
    override suspend fun finishLogin(captured: String): String = withContext(Dispatchers.IO) {
        val attempts = listOf(
            "" to JSONObject().put("code", captured).put("codeVerifier", verifier),
            "?code=${enc(captured)}" to JSONObject().put("codeVerifier", verifier),
            "" to JSONObject().put("authorizationCode", captured).put("codeVerifier", verifier)
        )
        var first: StoreHttp.Response? = null
        for ((query, body) in attempts) {
            val resp = post("token/new$query", body)
            if (resp.ok) return@withContext tokens(resp.body, null).secret()
            if (first == null) first = resp
        }
        throw IllegalStateException("REMA 1000 sign-in HTTP ${first!!.status}: ${snippet(first.body)}")
    }

    override suspend fun fetch(secret: String, since: Long): Pair<List<StoreReceipt>, String> = withContext(Dispatchers.IO) {
        val stored = Tokens.from(secret)
        if (stored.refresh.isEmpty() && !stored.usable()) throw StoreLoginExpired("REMA 1000 wants you to sign in again.")
        // Use the access token while it's valid and renew only when needed: a renewal may replace
        // the refresh token, and then the old one stops working.
        var tokens = if (stored.usable()) stored else renew(stored)
        try {
            val http = StoreHttp(AGENT)
            var resp = http.request("GET", "$API/user/receipts", headers(tokens))
            if (resp.status == 401 && tokens === stored && stored.refresh.isNotEmpty()) {
                tokens = renew(stored)
                resp = http.request("GET", "$API/user/receipts", headers(tokens))
            }
            if (!resp.ok) throw IllegalStateException("REMA 1000 receipts HTTP ${resp.status}: ${snippet(resp.body)}")
            val receipts = parseReceipts(resp.body).filter { it.receipt.purchasedAt >= since }
            receipts to tokens.secret()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (tokens !== stored) throw StoreFetchFailed(tokens.secret(), e) else throw e
        }
    }

    private fun renew(stored: Tokens): Tokens {
        val resp = post("token/renew", JSONObject().put("refreshToken", stored.refresh))
        if (resp.status == 400 || resp.status == 401) throw StoreLoginExpired("REMA 1000 wants you to sign in again.")
        if (!resp.ok) throw IllegalStateException("REMA 1000 token HTTP ${resp.status}: ${snippet(resp.body)}")
        return tokens(resp.body, stored.refresh)
    }

    private fun post(path: String, body: JSONObject): StoreHttp.Response =
        StoreHttp(AGENT).request(
            "POST", "$API/$path", mapOf("Accept" to "application/json"),
            body.toString().toByteArray(), "application/json"
        )

    private fun headers(tokens: Tokens) = mapOf(
        "Authorization" to "Bearer ${tokens.access}",
        "Accept" to "application/json"
    )

    /** The sign-in kept (encrypted) on the phone. [expires] is when the access token runs out. */
    private class Tokens(val access: String, val refresh: String, val expires: Long) {
        fun usable() = access.isNotEmpty() && expires > System.currentTimeMillis() + 60_000

        fun secret(): String = JSONObject().put("access", access).put("refresh", refresh).put("expires", expires).toString()

        companion object {
            fun from(secret: String): Tokens = JSONObject(secret).let {
                Tokens(it.optString("access"), it.optString("refresh"), it.optLong("expires", 0L))
            }
        }
    }

    companion object {
        private const val CLIENT_ID = "scan-and-pay"
        private const val AUTH = "https://cphapp.rema1000.dk"
        private const val API = "https://apim-rema-scan-prod.azure-api.net/v1"
        private const val REDIRECT = "dk.rema1000.app://logincallback"
        private const val AGENT = "okhttp/4.12.0"

        private fun enc(v: String) = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

        /** A short, one-line piece of a response body, for an error message. */
        private fun snippet(body: String) = body.replace(Regex("\\s+"), " ").trim().take(300)

        /** Access and refresh token from a token response, which may wrap them in "data". */
        private fun tokens(body: String, oldRefresh: String?): Tokens {
            val root = JSONObject(body)
            val json = root.optJSONObject("data") ?: root
            val access = StoreJson.text(json, "accessToken") ?: StoreJson.text(json, "access_token")
                ?: throw StoreLoginExpired("REMA 1000 didn't return a sign-in.")
            val refresh = StoreJson.text(json, "refreshToken") ?: StoreJson.text(json, "refresh_token") ?: oldRefresh.orEmpty()
            val lifetime = json.optLong("expiresIn", json.optLong("expires_in", 0L))
            val expires = if (lifetime > 0) System.currentTimeMillis() + lifetime * 1000
                else jwtExp(access)?.let { it * 1000 } ?: 0L
            return Tokens(access, refresh, expires)
        }

        private fun jwtExp(jwt: String): Long? = runCatching {
            val payload = jwt.split('.').getOrNull(1) ?: return null
            JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))).optLong("exp").takeIf { it > 0 }
        }.getOrNull()

        private val LIST_KEYS = listOf("receipts", "data", "items", "results", "content")
        private val ITEM_KEYS = listOf("items", "receiptItems", "lines", "products")

        /**
         * The receipts in a user/receipts response. Its layout isn't confirmed, so it's read by the
         * field names the Scan Selv app stores receipts under (totalPrice, paymentDate,
         * shelfText1, price, quantity, discount, …), wherever the list sits. When there is a list
         * but no receipt can be read, the error names the fields found (never their values), so
         * the reading can be fixed.
         */
        internal fun parseReceipts(body: String): List<StoreReceipt> {
            val text = body.trim()
            if (text.isEmpty()) return emptyList()
            val list = findList(if (text.startsWith("[")) JSONArray(text) else JSONObject(text))
                ?: throw IllegalStateException("REMA 1000 receipts: no list in the response (fields: ${fieldNames(JSONObject(text))})")
            if (list.length() == 0) return emptyList()
            val receipts = (0 until list.length()).mapNotNull { i -> list.optJSONObject(i)?.let { runCatching { parseReceipt(it) }.getOrNull() } }
            if (receipts.isEmpty()) {
                throw IllegalStateException("REMA 1000 receipts: couldn't read a receipt (fields: ${list.optJSONObject(0)?.let(::fieldNames) ?: "?"})")
            }
            return receipts
        }

        private fun findList(node: Any?, depth: Int = 0): JSONArray? = when {
            node is JSONArray -> node
            node is JSONObject && depth < 3 -> LIST_KEYS.firstNotNullOfOrNull { key -> findList(node.opt(key), depth + 1) }
            else -> null
        }

        /** Field names of an object and of its nested objects/first array element, no values. */
        private fun fieldNames(o: JSONObject, depth: Int = 0): String = o.keys().asSequence().joinToString(", ") { key ->
            val child = when (val v = o.opt(key)) {
                is JSONObject -> v
                is JSONArray -> v.optJSONObject(0)
                else -> null
            }
            if (child != null && depth < 2) "$key{${fieldNames(child, depth + 1)}}" else key
        }.take(600)

        private fun time(o: JSONObject, vararg keys: String): Long? {
            for (key in keys) {
                if (!o.has(key) || o.isNull(key)) continue
                val v = o.opt(key)
                if (v is Number) return epochMillis(v.toLong())
                StoreJson.text(o, key)?.let { s -> s.toLongOrNull()?.let { return epochMillis(it) } ?: StoreJson.time(s)?.let { return it } }
            }
            return null
        }

        private fun epochMillis(v: Long): Long? = if (v <= 0L) null else if (v < 1_000_000_000_000L) v * 1000 else v

        internal fun parseReceipt(r: JSONObject): StoreReceipt {
            val id = StoreJson.text(r, "id") ?: StoreJson.text(r, "displayId") ?: StoreJson.text(r, "receiptId")
                ?: throw IllegalStateException("No receipt id.")
            val at = time(r, "paymentDate", "purchaseDate", "date", "createdAt") ?: throw IllegalStateException("No date.")
            val lines = ITEM_KEYS.firstNotNullOfOrNull { r.optJSONArray(it) } ?: JSONArray()
            val parsed = (0 until lines.length()).mapNotNull { i -> lines.optJSONObject(i)?.let(::line) }
            val total = StoreJson.number(r, "totalPrice") ?: StoreJson.number(r, "total") ?: StoreJson.number(r, "amount")
            val items = amounts(parsed, total)
            val store = r.optJSONObject("store")
            val storeName = (StoreJson.text(store, "name") ?: StoreJson.text(r, "storeName"))
                ?.let { if (it.contains("rema", ignoreCase = true)) it else "REMA 1000 $it" } ?: "REMA 1000"
            return StoreReceipt(
                ParsedReceipt(
                    storeName = storeName,
                    purchasedAt = at,
                    total = total ?: items.sumOf { it.amount },
                    discount = StoreJson.number(r, "totalDiscount")?.let { abs(it) } ?: items.sumOf { it.discount },
                    receiptNumber = "rema:$id",
                    items = items
                )
            )
        }

        /** One receipt line as found: its text and summed price parts (a line can have several). */
        private class Line(val name: String, val price: Double, val deposit: Double, val discount: Double, val quantity: Double)

        private fun line(o: JSONObject): Line? {
            val name = listOfNotNull(StoreJson.text(o, "shelfText1"), StoreJson.text(o, "shelfText2"))
                .joinToString(" ").ifEmpty { null }
                ?: StoreJson.text(o, "name") ?: StoreJson.text(o, "description") ?: StoreJson.text(o, "text") ?: return null
            val parts = (o.optJSONArray("prices") ?: o.optJSONArray("priceItems"))
                ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: listOf(o)
            val price = parts.sumOf { StoreJson.number(it, "price") ?: StoreJson.number(it, "amount") ?: 0.0 }
            if (price == 0.0) return null
            return Line(
                name.trim().take(80),
                price,
                parts.sumOf { abs(StoreJson.number(it, "deposit") ?: 0.0) },
                parts.sumOf { abs(StoreJson.number(it, "discount") ?: 0.0) },
                parts.sumOf { StoreJson.number(it, "quantity") ?: 0.0 }.takeIf { it > 0 } ?: 1.0
            )
        }

        /** What each line cost. Whether a line's price is before or after its discount, and with
         * or without deposit (pant), isn't confirmed; the reading that adds up to the receipt's
         * total is used, else price minus discount. */
        private fun amounts(lines: List<Line>, total: Double?): List<ParsedReceiptItem> {
            val readings = listOf<(Line) -> Double>(
                { it.price - it.discount },
                { it.price },
                { it.price + it.deposit - it.discount },
                { it.price + it.deposit }
            )
            val pick = total?.let { t -> readings.firstOrNull { f -> abs(lines.sumOf(f) - t) < 0.05 } } ?: readings[0]
            return lines.map { ParsedReceiptItem(it.name, it.quantity, pick(it), it.discount, null) }.filter { it.amount > 0 }
        }
    }
}
