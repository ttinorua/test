package com.financetracker.app.data.prefs

import android.content.Context
import com.financetracker.app.data.ai.InsightCard
import com.financetracker.app.data.ai.InsightTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Caches the last AI-generated dashboard insight cards so they survive navigating away and
 * back without re-billing an API call. Generation itself is always user-initiated
 * (a button), never automatic, to keep AI spend predictable.
 */
object AiInsightsCache {
    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_INSIGHTS = "ai_insights_cards"

    private lateinit var prefs: android.content.SharedPreferences
    private val _insights = MutableStateFlow<List<InsightCard>>(emptyList())
    val insights: StateFlow<List<InsightCard>> get() = _insights

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _insights.value = deserialize(prefs.getString(KEY_INSIGHTS, null))
    }

    fun save(cards: List<InsightCard>) {
        _insights.value = cards
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY_INSIGHTS, serialize(cards)).apply()
        }
    }

    /** Dismisses the current insights (e.g. the dashboard card's close button) without billing
     * a new API call — [generateInsights][com.financetracker.app.ui.screens.dashboard.DashboardViewModel.generateInsights]
     * remains the only way to get new ones. */
    fun clear() {
        _insights.value = emptyList()
        if (::prefs.isInitialized) {
            prefs.edit().remove(KEY_INSIGHTS).apply()
        }
    }

    private fun serialize(cards: List<InsightCard>): String {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(
                JSONObject().apply {
                    put("label", card.label)
                    put("value", card.value)
                    put("detail", card.detail)
                    put("tone", card.tone.name)
                }
            )
        }
        return array.toString()
    }

    private fun deserialize(raw: String?): List<InsightCard> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                InsightCard(
                    label = obj.getString("label"),
                    value = obj.getString("value"),
                    detail = obj.getString("detail"),
                    tone = try {
                        InsightTone.valueOf(obj.getString("tone"))
                    } catch (e: IllegalArgumentException) {
                        InsightTone.NEUTRAL
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
