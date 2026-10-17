package com.financetracker.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.financetracker.app.util.MerchantLogos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The person's own name and logo for a company: "every transaction containing HBO is called
 * HBO Max and shows hbo.com's logo". Wins over the built-in list and the AI lookup.
 *
 * Kept in finance_prefs, so it's part of every full backup and comes back with a restore.
 */
data class MerchantOverride(
    /** Normalised word(s) a transaction's text must contain, e.g. "hbo". */
    val keyword: String,
    /** Name shown in the transaction list; null = automatic. */
    val name: String?,
    /** Website whose logo is shown; null = automatic, "" = no logo (category icon). */
    val domain: String?,
    val updatedAt: Long
)

object MerchantOverrides {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY = "merchant_overrides"

    private var prefs: SharedPreferences? = null
    private val _rules = MutableStateFlow<List<MerchantOverride>>(emptyList())
    val rules: StateFlow<List<MerchantOverride>> get() = _rules

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _rules.value = deserialize(prefs?.getString(KEY, null))
    }

    /** Saves the name and logo for every transaction containing [keyword], replacing an earlier
     * choice for the same word. Blank [name] = automatic; [domain] null = automatic, "" = no logo. */
    fun set(keyword: String, name: String?, domain: String?) {
        val key = MerchantLogos.normalize(keyword)
        if (key.isEmpty()) return
        val rule = MerchantOverride(key, name?.trim()?.ifEmpty { null }, domain, System.currentTimeMillis())
        save(_rules.value.filter { MerchantLogos.normalize(it.keyword) != key } + rule)
    }

    /** Back to automatic for [keyword]. */
    fun remove(keyword: String) {
        val key = MerchantLogos.normalize(keyword)
        save(_rules.value.filter { MerchantLogos.normalize(it.keyword) != key })
    }

    private fun save(list: List<MerchantOverride>) {
        _rules.value = list
        prefs?.edit()?.putString(KEY, serialize(list))?.apply()
    }

    /** The rule [description] falls under: the longest matching word wins (so "hbo max" beats
     * "hbo"), then the newest. */
    fun ruleFor(description: String, rules: List<MerchantOverride> = _rules.value): MerchantOverride? =
        rules.filter { MerchantLogos.containsWords(description, it.keyword) }
            .maxWithOrNull(compareBy<MerchantOverride> { it.keyword.length }.thenBy { it.updatedAt })

    /** The person's name for [description], or null to fall back to the automatic one. */
    fun nameFor(description: String, rules: List<MerchantOverride> = _rules.value): String? =
        rules.filter { it.name != null && MerchantLogos.containsWords(description, it.keyword) }
            .maxWithOrNull(compareBy<MerchantOverride> { it.keyword.length }.thenBy { it.updatedAt })
            ?.name

    /** The person's logo choice for [description]: a domain, "" for no logo, or null (automatic). */
    fun domainFor(description: String, rules: List<MerchantOverride> = _rules.value): String? =
        rules.filter { it.domain != null && MerchantLogos.containsWords(description, it.keyword) }
            .maxWithOrNull(compareBy<MerchantOverride> { it.keyword.length }.thenBy { it.updatedAt })
            ?.domain

    internal fun serialize(list: List<MerchantOverride>): String = JSONArray().apply {
        list.forEach { r ->
            put(
                JSONObject()
                    .put("keyword", r.keyword)
                    .put("name", r.name ?: JSONObject.NULL)
                    .put("domain", r.domain ?: JSONObject.NULL)
                    .put("updatedAt", r.updatedAt)
            )
        }
    }.toString()

    internal fun deserialize(text: String?): List<MerchantOverride> = runCatching {
        val array = JSONArray(text ?: return emptyList())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            MerchantOverride(
                keyword = o.getString("keyword"),
                name = if (o.isNull("name")) null else o.getString("name"),
                domain = if (o.isNull("domain")) null else o.getString("domain"),
                updatedAt = o.optLong("updatedAt")
            )
        }.filter { it.keyword.isNotEmpty() }
    }.getOrDefault(emptyList())
}
