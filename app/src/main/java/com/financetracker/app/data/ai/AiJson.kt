package com.financetracker.app.data.ai

import org.json.JSONObject

/** The JSON shapes shared by the Gemini and Groq connections: the dashboard insight cards and the
 * Ask AI propose_budget function (Claude's own tool definitions live in [ClaudeService]). */
internal object AiJson {

    const val PROPOSE_BUDGET_DESCRIPTION =
        "Propose a monthly budget based on the user's transaction history, for whatever time range or " +
            "categories they ask about. This only shows a proposal for the user to review; it does NOT " +
            "apply the budget automatically."

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

    fun proposeBudgetParameters(): JSONObject = JSONObject("""
        {
          "type": "object",
          "properties": {
            "account_name": {"type": "string", "description": "Exact account name from the ACCOUNTS list this budget applies to, or \"All accounts\" for a combined budget."},
            "overall_amount": {"type": "number", "description": "Suggested overall monthly spending limit across all expense categories combined. Omit if not proposing one."},
            "category_budgets": {
              "type": "array",
              "description": "Suggested monthly limits for specific categories.",
              "items": {
                "type": "object",
                "properties": {
                  "main_category": {"type": "string"},
                  "category": {"type": "string"},
                  "amount": {"type": "number"}
                },
                "required": ["main_category", "category", "amount"]
              }
            },
            "summary": {"type": "string", "description": "One or two sentences explaining the reasoning behind this budget proposal, to show the user."}
          }
        }
    """.trimIndent())

    fun parseBudgetProposal(args: JSONObject): BudgetProposal? {
        val overall = if (args.has("overall_amount")) args.optDouble("overall_amount").takeIf { !it.isNaN() } else null
        val items = args.optJSONArray("category_budgets")
        val categories = (0 until (items?.length() ?: 0)).mapNotNull { i ->
            val item = items?.optJSONObject(i) ?: return@mapNotNull null
            val main = item.optString("main_category").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val category = item.optString("category").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val amount = item.optDouble("amount").takeIf { !it.isNaN() } ?: return@mapNotNull null
            BudgetCategoryProposal(main, category, amount)
        }
        if (overall == null && categories.isEmpty()) return null
        return BudgetProposal(
            accountName = args.optString("account_name").takeIf { it.isNotBlank() },
            overallAmount = overall,
            categoryBudgets = categories,
            summary = args.optString("summary").takeIf { it.isNotBlank() }
        )
    }
}
