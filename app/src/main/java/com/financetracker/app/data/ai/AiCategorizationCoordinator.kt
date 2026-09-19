package com.financetracker.app.data.ai

import com.financetracker.app.data.repository.FinanceRepository

data class CategorizationProgress(val done: Int, val total: Int)

/** [remaining] is how many transactions were still uncategorized after this run — nonzero means
 * the caller stopped early (hit [AiCategorizationCoordinator.categorizeUncategorized]'s
 * [maxGroups] limit) and more work is left. */
data class CategorizationOutcome(val categorizedCount: Int, val totalConsidered: Int, val remaining: Int)

/**
 * One-time backfill for transactions that never got a real category — mainly the bulk of
 * history Enable Banking sync imports (it sends no category data at all), but this covers any
 * source. Groups transactions by their normalized note first, so a merchant that appears
 * hundreds of times across the history only costs one AI call instead of one per transaction.
 *
 * A full backfill can mean thousands of sequential AI calls (30-90+ minutes), far longer than
 * Android lets an ordinary background job run in one go (~10 minutes before the system stops
 * it) — so this is designed to be called repeatedly in bounded batches ([maxGroups] merchant
 * groups at a time) rather than run to completion in a single call. It never loses progress
 * between calls: each call re-reads whichever transactions are still uncategorized right now,
 * so anything already categorized in an earlier batch is naturally excluded, and [knownTotal]
 * (pass back whatever a previous call returned as [CategorizationOutcome.totalConsidered]) keeps
 * the reported progress counting up across the whole run instead of resetting each batch.
 */
object AiCategorizationCoordinator {

    suspend fun categorizeUncategorized(
        repository: FinanceRepository,
        maxGroups: Int = Int.MAX_VALUE,
        knownTotal: Int? = null,
        onProgress: suspend (CategorizationProgress) -> Unit
    ): CategorizationOutcome {
        if (!ClaudeService.isConfigured) return CategorizationOutcome(0, knownTotal ?: 0, 0)

        val categories = repository.getCategories()
        val uncategorizedIds = categories
            .filter { it.name == "Uncategorized" && it.mainCategory == "Uncategorized" }
            .map { it.id }
            .toSet()

        val allTransactions = repository.getAllTransactions()
        val targets = allTransactions.filter { it.categoryId == null || it.categoryId in uncategorizedIds }
        if (targets.isEmpty()) return CategorizationOutcome(0, knownTotal ?: 0, 0)

        val total = knownTotal ?: targets.size
        // How many were already categorized in earlier batches of this same run, derived purely
        // from current DB state vs. the run's original total — no separate counter to persist.
        val alreadyDone = total - targets.size

        val byNote = targets.groupBy { it.note.trim().lowercase() }
        var categorizedCount = 0
        var doneThisRun = 0
        var groupsProcessed = 0
        onProgress(CategorizationProgress(alreadyDone, total))

        for (group in byNote.values) {
            if (groupsProcessed >= maxGroups) break
            groupsProcessed++
            val sampleNote = group.first().note
            val match = CategorySuggester.suggest(sampleNote, categories).getOrNull()
            if (match != null && match.id !in uncategorizedIds) {
                for (transaction in group) {
                    repository.updateTransaction(transaction.copy(categoryId = match.id))
                    categorizedCount++
                }
            }
            doneThisRun += group.size
            onProgress(CategorizationProgress(alreadyDone + doneThisRun, total))
        }

        return CategorizationOutcome(categorizedCount, total, remaining = targets.size - doneThisRun)
    }
}
