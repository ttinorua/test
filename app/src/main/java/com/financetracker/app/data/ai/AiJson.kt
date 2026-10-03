package com.financetracker.app.data.ai

import org.json.JSONObject

/** The JSON shape of the dashboard insight cards, shared by the Gemini and Groq connections
 * (Claude's own tool definition lives in [ClaudeService]). */
internal object AiJson {

    /** For providers without schema-constrained output: the same shape, described in the prompt. */
    const val INSIGHTS_INSTRUCTIONS =
        "Reply with only a JSON object of the form {\"insights\": [{\"label\": string, \"value\": string, " +
            "\"detail\": string, \"tone\": \"positive\" | \"neutral\" | \"warning\"}]} holding 2 to 4 short, " +
            "concrete insights, most important first. label: a 2-4 word headline. value: the headline " +
            "figure, already formatted with the display currency or unit. detail: one short sentence of " +
            "context. tone: positive for good news, warning for something worth attention, else neutral."

    fun insightsSchema(): JSONObject = JSONObject("""
        {
          "type": "object",
          "properties": {
            "insights": {
              "type": "array",
              "description": "2 to 4 short, concrete insights, most important first.",
              "items": {
                "type": "object",
                "properties": {
                  "label": {"type": "string", "description": "Short headline, 2-4 words, e.g. \"Media spending\"."},
                  "value": {"type": "string", "description": "The headline figure, already formatted with the display currency or unit, e.g. \"3,319.99 kr\" or \"37% of outflows\"."},
                  "detail": {"type": "string", "description": "One short sentence of concrete context."},
                  "tone": {"type": "string", "enum": ["positive", "neutral", "warning"]}
                },
                "required": ["label", "value", "detail", "tone"]
              }
            }
          },
          "required": ["insights"]
        }
    """.trimIndent())

    fun parseInsights(text: String): List<InsightCard> = runCatching {
        val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val items = json.optJSONArray("insights") ?: return emptyList()
        (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val label = item.optString("label").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = item.optString("value").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tone = when (item.optString("tone").lowercase()) {
                "positive" -> InsightTone.POSITIVE
                "warning" -> InsightTone.WARNING
                else -> InsightTone.NEUTRAL
            }
            InsightCard(label, value, item.optString("detail"), tone)
        }
    }.getOrDefault(emptyList())
}
