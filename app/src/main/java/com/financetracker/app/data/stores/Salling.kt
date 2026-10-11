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
import java.util.UUID
import kotlin.math.abs

/**
 * Salling Group chains (Netto+ now; Bilka+/Føtex+ later). Sign-in is Salling's own login page the
 * store's app uses (OAuth with PKCE); the page returns a one-time code, which the app swaps for
 * tokens. Receipts come from the customer-programme backend the app reads. Unofficial, based on the
 * owner's own inspection of the Netto+ app; it can stop working if Salling changes it or starts
 * requiring the official app (device attestation).
 */
class Salling private constructor(
    override val store: Store,
    private val tenantId: String,
    private val tenantAlias: String,
    private val brandName: String
) : StoreConnector {

    private val verifier: String = ByteArray(48).also { SecureRandom().nextBytes(it) }
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
    private val installId: String = UUID.randomUUID().toString()

    override val needsEmailFirst = true

    override fun loginUrl(emailHint: String?): String {
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "$LOGIN/apps?clientId=$CLIENT_ID&tenantId=$tenantId&channel=CustomerProgram" +
            "&clientFlow=gigya&nonce=${randomToken()}&clientTraceId=${randomToken()}" +
            "&emailOrPhone=${enc(emailHint.orEmpty().trim())}" +
            "&code_challenge_method=S256&code_challenge=$challenge"
    }

    /** The login finishes at http://localhost with the one-time code in the query or the fragment. */
    override fun captureFromUrl(url: String): String? {
        if (!url.startsWith(REDIRECT)) return null
        Uri.parse(url).getQueryParameter("code")?.let { return it }
        val fragment = url.substringAfter('#', "")
        return Regex("code=([^&]+)").find(fragment)?.groupValues?.get(1)?.let { Uri.decode(it) }
    }

    override suspend fun finishLogin(captured: String): String = withContext(Dispatchers.IO) {
        token(
            "grant_type" to "authorization_code",
            "code" to captured,
            "code_verifier" to verifier,
            "client_id" to CLIENT_ID
        ).secret()
    }

    override suspend fun fetch(secret: String, since: Long): Pair<List<StoreReceipt>, String> = withContext(Dispatchers.IO) {
        val stored = Tokens.from(secret)
        if (stored.refresh.isEmpty() && !stored.usable()) throw StoreLoginExpired("$brandName wants you to sign in again.")
        // Like the Netto+ app: use the access token while it's valid and renew only when needed.
        // Each renewal replaces the refresh token and the old one stops working.
        var tokens = if (stored.usable()) stored else renew(stored)
        try {
            val http = StoreHttp(AGENT)
            var listResp = http.request("GET", "$BACKEND/api/cp/receipt", backendHeaders(tokens))
            if (tokens === stored && stored.refresh.isNotEmpty() && looksExpired(listResp)) {
                tokens = renew(stored)
                listResp = http.request("GET", "$BACKEND/api/cp/receipt", backendHeaders(tokens))
            }
            // Surface the server's own message (status + a snippet) so a rejected request can be
            // diagnosed instead of showing a bare "sign in again".
            if (!listResp.ok) throw IllegalStateException("$brandName receipts HTTP ${listResp.status}: ${snippet(listResp.body)}")
            val headers = backendHeaders(tokens)
            val wanted = parseList(listResp.body).filter { it.postingAt >= since }
            val receipts = wanted.mapNotNull { summary ->
                val query = "receiptId=${enc(summary.id)}" + (summary.type?.let { "&type=${enc(it)}" } ?: "")
                val detail = http.request("GET", "$BACKEND/api/cp/receipt/details?$query", headers)
                if (!detail.ok) null else runCatching { parseDetails(brandName, summary, detail.body) }.getOrNull()
            }
            receipts to tokens.secret()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The sign-in was renewed before this failed: hand the new one back so it's kept.
            if (tokens !== stored) throw StoreFetchFailed(tokens.secret(), e) else throw e
        }
    }

    private fun renew(stored: Tokens): Tokens =
        token("grant_type" to "refresh_token", "refresh_token" to stored.refresh, "client_id" to CLIENT_ID)

    /** The backend says the access token has run out (the app then renews it and retries). */
    private fun looksExpired(resp: StoreHttp.Response) =
        resp.status == 401 || (!resp.ok && resp.body.contains("Expired", ignoreCase = true))

    /** Access, id and refresh tokens from Salling's token service. */
    private fun token(vararg fields: Pair<String, String>): Tokens {
        val body = fields.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }.toByteArray()
        val resp = StoreHttp(AGENT).request(
            "POST", "$TOKEN/token", mapOf("Accept" to "application/json"), body, "application/x-www-form-urlencoded"
        )
        // invalid_grant: the code or refresh token is used up or revoked; only a new sign-in helps.
        if (resp.status == 400 && resp.body.contains("invalid_grant")) throw StoreLoginExpired("$brandName wants you to sign in again.")
        if (!resp.ok) throw IllegalStateException("$brandName token HTTP ${resp.status}: ${snippet(resp.body)}")
        val json = JSONObject(resp.body)
        val access = json.firstString("access_token", "accessToken")
        val id = json.firstString("id_token", "idToken")
        val refresh = json.firstString("refresh_token", "refreshToken").ifEmpty { fields.toMap()["refresh_token"].orEmpty() }
        if (access.isEmpty() && id.isEmpty()) throw StoreLoginExpired("$brandName didn't return a sign-in.")
        val lifetime = json.optLong("expires_in", json.optLong("expiresIn", 0L))
        val expires = if (lifetime > 0) System.currentTimeMillis() + lifetime * 1000
            else jwtClaim(access.ifEmpty { id }, "exp")?.toLongOrNull()?.let { it * 1000 } ?: 0L
        return Tokens(access, id, refresh, expires)
    }

    /** The headers the Salling backend expects; without them it answers 401. */
    private fun backendHeaders(tokens: Tokens): Map<String, String> {
        val headers = linkedMapOf(
            "Authorization" to "Bearer ${tokens.access.ifEmpty { tokens.id }}",
            "Accept" to "application/json",
            "X-ClientAppName" to "CustomerProgram-android",
            "X-DevicePlatform" to "android",
            "X-DeviceOS" to "android",
            "X-DeviceOSVersion" to "14",
            "X-DeviceName" to "Pixel 7",
            "X-DeviceBrand" to "Google",
            "X-CommitID" to "d2e5bbeda",
            "X-AppVersion" to APP_VERSION,
            "X-ClientVersion" to APP_VERSION,
            "X-ClientTraceId" to installId,
            "X-OperationTraceId" to "OT-${randomToken().take(10)}",
            "X-tenantAlias" to tenantAlias
        )
        if (tokens.id.isNotEmpty()) {
            headers["x-id_token"] = "Bearer ${tokens.id}"
            jwtClaim(tokens.id, "sub")?.let { headers["x-idpid"] = it }
        }
        return headers
    }

    /** The sign-in kept (encrypted) on the phone. [expires] is when the access token runs out. */
    private class Tokens(val access: String, val id: String, val refresh: String, val expires: Long) {
        fun usable() = (access.isNotEmpty() || id.isNotEmpty()) && expires > System.currentTimeMillis() + 60_000

        fun secret(): String = JSONObject()
            .put("refresh", refresh).put("access", access).put("id", id).put("expires", expires)
            .toString()

        companion object {
            fun from(secret: String): Tokens = JSONObject(secret).let {
                Tokens(it.optString("access"), it.optString("id"), it.optString("refresh"), it.optLong("expires", 0L))
            }
        }
    }

    /** One receipt from the list, enough to decide whether to fetch its details. */
    internal class Summary(val id: String, val type: String?, val postingAt: Long, val storeName: String?, val total: Double?)

    companion object {
        private const val CLIENT_ID = "customer-program"
        private const val LOGIN = "https://p-idp.dsgapps.dk"
        private const val TOKEN = "https://idp.dsgapps.dk"
        private const val BACKEND = "https://p-club.dsgapps.dk"
        private const val REDIRECT = "http://localhost"
        private const val APP_VERSION = "2.9.5.1090"
        private const val AGENT = "okhttp/4.12.0"

        /** Netto+. Bilka+ and Føtex+ use the same flow with their own tenant id/alias, added once
         * those are confirmed the same way (they are not wired up yet). */
        fun netto() = Salling(Store.NETTO, tenantId = "4", tenantAlias = "TID-2Y7JRG", brandName = "Netto")

        private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

        /** A short, one-line piece of a response body, for an error message. */
        private fun snippet(body: String) = body.replace(Regex("\\s+"), " ").trim().take(300)

        private fun randomToken(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

        private fun JSONObject.firstString(vararg keys: String): String {
            for (k in keys) optString(k).let { if (it.isNotEmpty() && it != "null") return it }
            return ""
        }

        /** One claim of a JWT, e.g. "sub" (the account id the backend wants in x-idpid) or "exp". */
        private fun jwtClaim(jwt: String, claim: String): String? = runCatching {
            val payload = jwt.split('.').getOrNull(1) ?: return null
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            JSONObject(json).optString(claim).takeIf { it.isNotEmpty() }
        }.getOrNull()

        /** Epoch seconds or millis → millis; 0 or missing → null. */
        internal fun epochMillis(v: Long): Long? =
            if (v <= 0L) null else if (v < 1_000_000_000_000L) v * 1000 else v

        /** The receipt list (CPReceiptTemp → receiptsList → receipts). */
        internal fun parseList(bodyText: String): List<Summary> {
            val groups = JSONObject(bodyText).optJSONArray("receiptsList") ?: return emptyList()
            val out = mutableListOf<Summary>()
            for (gi in 0 until groups.length()) {
                val receipts = groups.optJSONObject(gi)?.optJSONArray("receipts") ?: continue
                for (ri in 0 until receipts.length()) {
                    val r = receipts.optJSONObject(ri) ?: continue
                    val id = StoreJson.text(r, "id") ?: continue
                    val posting = epochMillis(r.optLong("postingDate", 0L)) ?: continue
                    out += Summary(
                        id,
                        StoreJson.text(r, "type") ?: StoreJson.text(r, "itemType"),
                        posting,
                        StoreJson.text(r, "storeName"),
                        StoreJson.number(r, "salesTotal")
                    )
                }
            }
            return out
        }

        /** One receipt's details (a list of CPReceiptDetailHeader; the first is the receipt). */
        internal fun parseDetails(brandName: String, summary: Summary, bodyText: String): StoreReceipt {
            val arr = JSONArray(bodyText)
            val header = (0 until arr.length()).firstNotNullOfOrNull { arr.optJSONObject(it) }
                ?: throw IllegalStateException("No receipt detail.")
            val lines = header.optJSONArray("lineItems") ?: JSONArray()
            val items = mutableListOf<ParsedReceiptItem>()
            for (i in 0 until lines.length()) {
                val li = lines.optJSONObject(i) ?: continue
                val name = StoreJson.text(li, "articleDescription") ?: continue
                val amount = StoreJson.number(li, "salesTotal") ?: StoreJson.number(li, "salesPrice") ?: continue
                if (amount <= 0) continue
                val qty = StoreJson.number(li, "qtyInSalesUnit") ?: StoreJson.number(li, "qtyInBaseUnit") ?: 1.0
                items += ParsedReceiptItem(
                    name.trim().take(80),
                    qty.takeIf { it > 0 } ?: 1.0,
                    amount,
                    abs(StoreJson.number(li, "discountTotal") ?: 0.0),
                    null
                )
            }
            val storeName = (StoreJson.text(header, "storeName") ?: summary.storeName)?.let {
                if (it.contains(brandName, ignoreCase = true)) it else "$brandName $it"
            } ?: brandName
            return StoreReceipt(
                ParsedReceipt(
                    storeName = storeName,
                    purchasedAt = epochMillis(header.optLong("postingDate", 0L)) ?: summary.postingAt,
                    total = StoreJson.number(header, "salesTotal") ?: summary.total ?: items.sumOf { it.amount },
                    discount = abs(StoreJson.number(header, "discountTotal") ?: 0.0),
                    receiptNumber = "salling:${summary.id}",
                    items = items
                )
            )
        }
    }
}
