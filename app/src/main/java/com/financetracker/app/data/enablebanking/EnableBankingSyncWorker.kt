package com.financetracker.app.data.enablebanking

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.financetracker.app.FinanceApp
import java.util.concurrent.TimeUnit

/**
 * Runs [EnableBankingSyncCoordinator] as WorkManager-managed background work instead of a plain
 * application-scoped coroutine, so a sync that can take a long time (the very first sync, or any
 * sync after local data was wiped — see [EnableBankingSyncCoordinator]'s own doc) survives the
 * app being backgrounded or its process being killed, the same durability the AI categorization
 * backfill already has via [com.financetracker.app.data.ai.AiCategorizationWorker] — including
 * the same batching + [Result.retry] design (not a self-enqueue) to stay inside Android's ~10
 * minute execution ceiling for background work, with the run's total kept in SharedPreferences
 * since a retry replays the original request's input unchanged.
 */
class EnableBankingSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as FinanceApp).repository
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val knownTotal = prefs.getInt(KEY_PERSISTED_TOTAL, -1).takeIf { it >= 0 }

        val outcome = EnableBankingSyncCoordinator.syncSelectedAccounts(
            repository,
            maxGroups = BATCH_GROUPS,
            knownTotal = knownTotal
        ) { progress ->
            // total 0 is the fetch-phase "still fetching, not a real total yet" sentinel (see
            // EnableBankingSyncCoordinator) — only persist a real positive total, so a retry
            // right after an interruption mid-fetch never reads back a bogus 0 as knownTotal
            // (which would make alreadyDone go negative once the real total is computed).
            if (progress.total > 0) {
                prefs.edit().putInt(KEY_PERSISTED_TOTAL, progress.total).apply()
            }
            setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
        } ?: return Result.success()

        if (outcome.remaining > 0) {
            return Result.retry()
        }

        prefs.edit().remove(KEY_PERSISTED_TOTAL).apply()
        val output = workDataOf(
            KEY_IMPORTED to outcome.importedCount,
            KEY_SKIPPED to outcome.skippedCount,
            KEY_FAILURE to (outcome.failureMessage ?: "")
        )
        return Result.success(output)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "enable_banking_sync"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_IMPORTED = "imported"
        const val KEY_SKIPPED = "skipped"
        const val KEY_FAILURE = "failure"
        // Each Claude call now covers CategorySuggester.BATCH_SIZE (25) merchants at once (see
        // EnableBankingSyncCoordinator), so this many groups per invocation is still only ~40
        // sequential AI calls, not 1000.
        private const val BATCH_GROUPS = 1000
        private const val PREFS_NAME = "finance_prefs"
        private const val KEY_PERSISTED_TOTAL = "enable_banking_sync_total"

        /** A short, constant (not exponential) backoff between batches — a large first sync can
         * need several retries, and exponential backoff would quickly stretch the gaps between
         * batches to hours. */
        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EnableBankingSyncWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()

        /** Clears any total left over from a previous run so a fresh sync always starts by
         * computing (and reporting) a real, current total. */
        fun clearPersistedState(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PERSISTED_TOTAL)
                .apply()
        }
    }
}
