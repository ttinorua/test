package com.financetracker.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import com.financetracker.app.util.MerchantLogos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Finds the website of merchants the built-in list in [MerchantLogos] doesn't know, by asking the
 * AI chain, and remembers each answer on the phone so a merchant is only ever asked about once.
 *
 * Rows ask via [request] as they scroll into view; requests are gathered for a moment and sent
 * as one batched question, so opening a long list costs a few AI calls, not one per row. A failed
 * call isn't saved, so those merchants are tried again on the next app start.
 */
object MerchantLogoLookup {
    private const val PREFS_NAME = "merchant_logos"
    private const val KEY_DOMAINS = "domains"
    private const val BATCH_SIZE = 40

    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val pending = LinkedHashSet<String>()
    private val askedThisSession = HashSet<String>()
    private var batchJob: Job? = null

    /** Merchant key → domain; "" means the AI found no company (no logo). */
    private val _domains = MutableStateFlow<Map<String, String>>(emptyMap())
    val domains: StateFlow<Map<String, String>> get() = _domains

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _domains.value = runCatching {
            val json = JSONObject(prefs.getString(KEY_DOMAINS, null) ?: "{}")
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    /** The company domain for a transaction description, or null when there's no logo (yet). */
    fun domainFor(description: String, cache: Map<String, String> = _domains.value): String? =
        MerchantLogos.knownDomain(description)
            ?: MerchantLogos.merchantKey(description)?.let { cache[it] }?.ifBlank { null }

    /** Queues an AI lookup for [description] unless it's already known, cached or in flight. */
    fun request(description: String) {
        if (!AiService.isConfigured || MerchantLogos.knownDomain(description) != null) return
        val key = MerchantLogos.merchantKey(description) ?: return
        synchronized(lock) {
            if (key in _domains.value || !askedThisSession.add(key)) return
            pending += key
            if (batchJob == null) {
                batchJob = scope.launch {
                    delay(800) // let the visible rows all queue up first
                    drain()
                }
            }
        }
    }

    private suspend fun drain() {
        while (true) {
            // Clearing batchJob under the same lock as request() means a merchant queued just
            // as this finishes always starts a fresh batch instead of being stranded.
            val batch = synchronized(lock) {
                pending.take(BATCH_SIZE).also {
                    pending.removeAll(it.toSet())
                    if (it.isEmpty()) batchJob = null
                }
            }
            if (batch.isEmpty()) return
            val reply = AiService.ask(SYSTEM_PROMPT, JSONArray(batch).toString(), maxTokens = 2048L, quick = true)
            val found = reply.getOrNull()?.let { MerchantLogos.parseDomainReply(it, batch) } ?: continue
            save(found)
        }
    }

    private fun save(found: Map<String, String>) {
        val updated = _domains.value + found
        _domains.value = updated
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_DOMAINS, JSONObject(updated).toString()).apply()
        }
    }

    private const val SYSTEM_PROMPT =
        "You identify companies from bank transaction descriptions (mostly Danish). You get a JSON " +
            "array of descriptions. For each, give the company's main website domain, e.g. \"netto.dk\". " +
            "Use null for private persons, transfers between accounts, or when you are not sure. " +
            "Reply with only a JSON object mapping each description, exactly as given, to a domain " +
            "string or null."
}
