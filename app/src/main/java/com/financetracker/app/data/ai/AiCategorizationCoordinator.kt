package com.financetracker.app.data.ai

import com.financetracker.app.data.bank.LegacyCategories
import com.financetracker.app.data.db.entity.Category
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
 * A full backfill can mean thousands of transactions across hundreds of unique merchants, far
 * longer than Android lets an ordinary background job run in one go (~10 minutes before the
 * system stops it) — so this is designed to be called repeatedly in bounded batches
 * ([maxGroups] merchant groups at a time) rather than run to completion in a single call. It
 * never loses progress between calls: each call re-reads whichever transactions are still
 * uncategorized right now, so anything already categorized in an earlier batch is naturally
 * excluded, and [knownTotal] (pass back whatever a previous call returned as
 * [CategorizationOutcome.totalConsidered]) keeps the reported progress counting up across the
 * whole run instead of resetting each batch. Each merchant is first tried against
 * [LocalCategoryMatcher] (free, instant, no network call) before ever asking Claude — real
 * histories are usually dominated by a handful of recurring merchants (groceries, fuel,
 * subscriptions, salary), so this alone resolves a meaningful share of a large backfill for
 * free. Whatever's left unmatched is AI-suggested [CategorySuggester.BATCH_SIZE] at a time in a
 * single Claude call (see [CategorySuggester.suggestBatch]) instead of one call per merchant —
 * each call is a sequential network round trip, so together these are the main lever on how
 * long a large backfill actually takes wall-clock.
 *
 * Also picks up transactions sitting under one of [LegacyCategories]'s old generic buckets (e.g.
 * "Transportation", "Utilities") the same as literally-Uncategorized ones: those buckets predate
 * the real taxonomy and each could mean several different real categories, so rather than leave
 * them stuck there forever, every transaction under one is individually re-matched by its own
 * note text against the full real category list. The old bucket itself is excluded as a possible
 * suggestion, so a transaction only ever moves on to a real, specific category, or stays exactly
 * where it was if nothing matches confidently.
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
        val excludedIds = categories
            .filter { (it.name == "Uncategorized" && it.mainCategory == "Uncategorized") || LegacyCategories.isAmbiguousBucket(it) }
            .map { it.id }
            .toSet()

        val allTransactions = repository.getAllTransactions()
        val targets = allTransactions.filter { it.categoryId == null || it.categoryId in excludedIds }
        if (targets.isEmpty()) return CategorizationOutcome(0, knownTotal ?: 0, 0)

        val total = knownTotal ?: targets.size
        // How many were already categorized in earlier batches of this same run, derived purely
        // from current DB state vs. the run's original total — no separate counter to persist.
        val alreadyDone = total - targets.size

        val groupsToProcess = targets.groupBy { it.note.trim().lowercase() }.values.toList().take(maxGroups)
        var categorizedCount = 0
        var doneThisRun = 0
        onProgress(CategorizationProgress(alreadyDone, total))

        // Resolve whatever LocalCategoryMatcher can for free first; only the leftover unmatched
        // groups go into the batched AI call below.
        val localMatches = groupsToProcess.map { group -> LocalCategoryMatcher.suggest(group.first().note, categories) }
        val unresolvedIndices = localMatches.withIndex().filter { it.value == null }.map { it.index }

        val aiMatches = mutableMapOf<Int, Category?>()
        for (chunk in unresolvedIndices.chunked(CategorySuggester.BATCH_SIZE)) {
            val notes = chunk.map { groupsToProcess[it].first().note }
            val matches = CategorySuggester.suggestBatch(notes, categories).getOrNull()
            chunk.forEachIndexed { i, index -> aiMatches[index] = matches?.getOrNull(i) }
        }

        groupsToProcess.forEachIndexed { index, group ->
            val match = localMatches[index] ?: aiMatches[index]
            if (match != null && match.id !in excludedIds) {
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
