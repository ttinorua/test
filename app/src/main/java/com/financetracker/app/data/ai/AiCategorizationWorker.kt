package com.financetracker.app.data.ai

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
 * Runs [AiCategorizationCoordinator]'s backfill as WorkManager-managed background work instead
 * of a plain ViewModel coroutine, so a run that can take a long time (thousands of AI calls for
 * a real transaction history) survives navigating away from Settings, the screen turning off, or
 * the app being backgrounded — not just whether the Settings screen happens to stay open.
 *
 * A full backfill (thousands of transactions across hundreds of unique merchants) can take far
 * longer than Android lets an ordinary background job run before the system stops it (~10
 * minutes) — a real run was observed getting stopped and restarting the entire backfill from
 * scratch each time. Each execution now only processes [BATCH_GROUPS] merchant groups (safely
 * inside that time budget even with each Claude call itself now covering
 * [CategorySuggester.BATCH_SIZE] merchants — see [AiCategorizationCoordinator]) and, if work
 * remains, returns [Result.retry] — WorkManager's own built-in mechanism for "run this again" —
 * instead of manually enqueuing a replacement for its own still-current unique work, which is a
 * known way to race with and cancel yourself. The run's original total (needed so progress counts
 * up across the whole chain instead of resetting every batch) is kept in SharedPreferences rather
 * than WorkRequest input data, since a retry reuses the original request's input unchanged. The
 * same goes for the categorized count: [AiCategorizationCoordinator.categorizeUncategorized]
 * only ever counts what it categorized in *that* call, so without accumulating it here across
 * retries too, a multi-retry run's final message would report only the last batch's count (which
 * can easily be a small or even zero number) instead of the true total for the whole run — a
 * real run was observed reporting "Categorized 0 of N" despite having genuinely categorized
 * plenty of transactions in earlier batches.
 */
class AiCategorizationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as FinanceApp).repository
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val knownTotal = prefs.getInt(KEY_PERSISTED_TOTAL, -1).takeIf { it >= 0 }
        val categorizedSoFar = prefs.getInt(KEY_PERSISTED_CATEGORIZED, 0)

        val outcome = AiCategorizationCoordinator.categorizeUncategorized(
            repository,
            maxGroups = BATCH_GROUPS,
            knownTotal = knownTotal
        ) { progress ->
            prefs.edit().putInt(KEY_PERSISTED_TOTAL, progress.total).apply()
            setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
        }
        // Safe to sum across retries unlike, say, a "skipped/duplicate" count would be: once a
        // transaction is categorized it leaves the coordinator's uncategorized target set for
        // every later call, so it's never counted here twice.
        val cumulativeCategorized = categorizedSoFar + outcome.categorizedCount

        if (outcome.remaining > 0) {
            prefs.edit().putInt(KEY_PERSISTED_CATEGORIZED, cumulativeCategorized).apply()
            return Result.retry()
        }

        prefs.edit().remove(KEY_PERSISTED_TOTAL).remove(KEY_PERSISTED_CATEGORIZED).apply()
        val output = workDataOf(
            KEY_CATEGORIZED to cumulativeCategorized,
            KEY_TOTAL_CONSIDERED to outcome.totalConsidered
        )
        return Result.success(output)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ai_categorization"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CATEGORIZED = "categorized"
        const val KEY_TOTAL_CONSIDERED = "total_considered"
        // Each Claude call now covers CategorySuggester.BATCH_SIZE (25) merchants at once, so
        // this many groups per invocation is still only ~40 sequential AI calls, not 1000.
        private const val BATCH_GROUPS = 1000
        private const val PREFS_NAME = "finance_prefs"
        private const val KEY_PERSISTED_TOTAL = "ai_categorization_total"
        private const val KEY_PERSISTED_CATEGORIZED = "ai_categorization_categorized"

        /** A short, constant (not exponential) backoff between batches — this can be dozens of
         * retries for a large history, and exponential backoff would quickly stretch the gaps
         * between batches to hours. */
        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<AiCategorizationWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()

        /** Clears any total/count left over from a previous run so a fresh "Categorize with AI"
         * tap always starts by computing (and reporting) real, current numbers. */
        fun clearPersistedState(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PERSISTED_TOTAL)
                .remove(KEY_PERSISTED_CATEGORIZED)
                .apply()
        }
    }
}
