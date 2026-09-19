package com.financetracker.app.data.ai

import com.financetracker.app.data.db.entity.Category

/**
 * Matches a transaction description against the user's existing categories using Claude,
 * shared by the manual "suggest category" button and any automatic categorization (Enable
 * Banking sync, the bulk "Categorize with AI" backfill).
 */
object CategorySuggester {

    /** Returns the matched [Category], or null (a success with no confident match) — a plain
     * Kotlin exception/[Result.failure] is reserved for real request failures (not configured,
     * network error) so callers can tell "no match" apart from "couldn't even ask". */
    suspend fun suggest(note: String, categories: List<Category>): Result<Category?> {
        if (note.isBlank() || categories.isEmpty()) return Result.success(null)

        val categoryList = categories.joinToString("\n") { "${it.mainCategory}|${it.name}|${it.type}" }
        val systemPrompt =
            "You categorize personal finance transactions. Here are the user's existing " +
                "categories as MainCategory|Subcategory|Type (Type is INCOME or EXPENSE):\n" +
                categoryList +
                "\n\nGiven a transaction description, reply with ONLY the best matching " +
                "MainCategory|Subcategory from the list above, exactly as written, on a single " +
                "line. Do not invent new categories. If nothing fits well, reply with " +
                "Uncategorized|Uncategorized."

        return ClaudeService.ask(systemPrompt, note, maxTokens = 60L).map { reply ->
            val parts = reply.trim().lines().first().split("|").map { it.trim() }
            if (parts.size != 2) {
                null
            } else {
                categories.firstOrNull {
                    it.mainCategory.equals(parts[0], ignoreCase = true) && it.name.equals(parts[1], ignoreCase = true)
                }
            }
        }
    }
}
