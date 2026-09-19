package com.financetracker.app.data.ai

import com.financetracker.app.data.repository.FinanceRepository

data class CategorizationProgress(val done: Int, val total: Int)
data class CategorizationOutcome(val categorizedCount: Int, val totalConsidered: Int)

/**
 * One-time backfill for transactions that never got a real category — mainly the bulk of
 * history Enable Banking sync imports (it sends no category data at all), but this covers any
 * source. Groups transactions by their normalized note first, so a merchant that appears
 * hundreds of times across the history only costs one AI call instead of one per transaction.
 */
object AiCategorizationCoordinator {

    suspend fun categorizeUncategorized(
        repository: FinanceRepository,
        onProgress: suspend (CategorizationProgress) -> Unit
    ): CategorizationOutcome {
        if (!ClaudeService.isConfigured) return CategorizationOutcome(0, 0)

        val categories = repository.getCategories()
        val uncategorizedIds = categories
            .filter { it.name == "Uncategorized" && it.mainCategory == "Uncategorized" }
            .map { it.id }
            .toSet()

        val allTransactions = repository.getAllTransactions()
        val targets = allTransactions.filter { it.categoryId == null || it.categoryId in uncategorizedIds }
        if (targets.isEmpty()) return CategorizationOutcome(0, 0)

        val byNote = targets.groupBy { it.note.trim().lowercase() }
        var categorizedCount = 0
        var done = 0
        onProgress(CategorizationProgress(0, targets.size))

        for (group in byNote.values) {
            val sampleNote = group.first().note
            val match = CategorySuggester.suggest(sampleNote, categories).getOrNull()
            if (match != null && match.id !in uncategorizedIds) {
                for (transaction in group) {
                    repository.updateTransaction(transaction.copy(categoryId = match.id))
                    categorizedCount++
                }
            }
            done += group.size
            onProgress(CategorizationProgress(done, targets.size))
        }

        return CategorizationOutcome(categorizedCount, targets.size)
    }
}
