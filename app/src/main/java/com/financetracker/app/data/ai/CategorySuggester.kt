package com.financetracker.app.data.ai

import com.financetracker.app.data.db.entity.Category

/**
 * Matches a transaction description against the user's existing categories using Claude,
 * shared by the manual "suggest category" button and any automatic categorization (Enable
 * Banking sync, the bulk "Categorize with AI" backfill).
 */
object CategorySuggester {

    /** How many notes [suggestBatch] sends per Claude call — the single biggest lever on how
     * long a large backfill/sync takes, since each call is a sequential network round trip: one
     * call per this many merchants instead of one call per merchant. Kept modest (not larger)
     * so a single reply stays short and reliable to parse back correctly, line for line. */
    const val BATCH_SIZE = 25

    /** Returns the matched [Category], or null (a success with no confident match) — a plain
     * Kotlin exception/[Result.failure] is reserved for real request failures (not configured,
     * network error) so callers can tell "no match" apart from "couldn't even ask". */
    suspend fun suggest(note: String, categories: List<Category>): Result<Category?> {
        if (note.isBlank() || categories.isEmpty()) return Result.success(null)
        return suggestBatch(listOf(note), categories).map { it.first() }
    }

    /** Same matching as [suggest], but for up to [BATCH_SIZE] notes in a single Claude call
     * instead of one call per note — the input's order is preserved in the returned list
     * (including a null for any blank note, matched positionally, not by content). A single
     * request failure (not configured, network error) fails the whole batch, matching [suggest]'s
     * per-call failure semantics; callers processing more than [BATCH_SIZE] notes should chunk
     * accordingly. */
    suspend fun suggestBatch(notes: List<String>, categories: List<Category>): Result<List<Category?>> {
        if (categories.isEmpty()) return Result.success(notes.map { null })
        val nonBlank = notes.withIndex().filter { it.value.isNotBlank() }
        if (nonBlank.isEmpty()) return Result.success(notes.map { null })

        val categoryList = categories.joinToString("\n") { "${it.mainCategory}|${it.name}|${it.type}" }
        val numberedNotes = nonBlank.withIndex().joinToString("\n") { (n, entry) -> "${n + 1}. ${entry.value}" }
        val systemPrompt =
            "You categorize personal finance transactions. Here are the user's existing " +
                "categories as MainCategory|Subcategory|Type (Type is INCOME or EXPENSE):\n" +
                categoryList +
                "\n\nFor EACH numbered transaction description below, reply with the best " +
                "matching MainCategory|Subcategory from the list above, exactly as written. " +
                "Reply with exactly one line per numbered item, in the same order, formatted as " +
                "\"N. MainCategory|Subcategory\" (N matching the item's number). Do not invent " +
                "new categories, and do not skip, merge or add lines. If nothing fits well for " +
                "an item, reply \"N. Uncategorized|Uncategorized\" for that item."

        return ClaudeService.ask(systemPrompt, numberedNotes, maxTokens = 40L * nonBlank.size + 100L).map { reply ->
            val results = MutableList<Category?>(notes.size) { null }
            val byNumber = reply.trim().lines().mapNotNull { line ->
                val match = Regex("""^(\d+)\.\s*(.+)$""").find(line.trim()) ?: return@mapNotNull null
                match.groupValues[1].toIntOrNull()?.let { it to match.groupValues[2].trim() }
            }.toMap()

            nonBlank.forEachIndexed { n, (originalIndex, _) ->
                val raw = byNumber[n + 1] ?: return@forEachIndexed
                val parts = raw.split("|").map { it.trim() }
                if (parts.size == 2) {
                    results[originalIndex] = categories.firstOrNull {
                        it.mainCategory.equals(parts[0], ignoreCase = true) && it.name.equals(parts[1], ignoreCase = true)
                    }
                }
            }
            results
        }
    }
}
