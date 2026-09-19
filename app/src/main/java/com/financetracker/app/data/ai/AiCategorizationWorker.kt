package com.financetracker.app.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.financetracker.app.FinanceApp

/**
 * Runs [AiCategorizationCoordinator]'s backfill as WorkManager-managed background work instead
 * of a plain ViewModel coroutine, so a run that can take a long time (thousands of AI calls for
 * a real transaction history) survives navigating away from Settings, the screen turning off, or
 * the app being backgrounded — not just whether the Settings screen happens to stay open.
 *
 * A full backfill (thousands of sequential AI calls) can take far longer than Android lets an
 * ordinary background job run before the system stops it (~10 minutes) — a real run was
 * observed getting stopped and, without this batching, restarting the entire backfill from
 * scratch each time. Each execution now only processes [BATCH_GROUPS] merchant groups (safely
 * inside that time budget) and, if work remains, enqueues its own continuation under the same
 * unique work name before returning success — so the system sees a chain of short jobs instead
 * of one long one, and genuinely resumes rather than restarting.
 */
class AiCategorizationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as FinanceApp).repository
        val knownTotal = inputData.getInt(KEY_TOTAL, -1).takeIf { it >= 0 }

        val outcome = AiCategorizationCoordinator.categorizeUncategorized(
            repository,
            maxGroups = BATCH_GROUPS,
            knownTotal = knownTotal
        ) { progress ->
            setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
        }

        if (outcome.remaining > 0) {
            val continuation = OneTimeWorkRequestBuilder<AiCategorizationWorker>()
                .setInputData(workDataOf(KEY_TOTAL to outcome.totalConsidered))
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, continuation)
        }

        val output = workDataOf(
            KEY_CATEGORIZED to outcome.categorizedCount,
            KEY_TOTAL_CONSIDERED to outcome.totalConsidered,
            KEY_REMAINING to outcome.remaining
        )
        return Result.success(output)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ai_categorization"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CATEGORIZED = "categorized"
        const val KEY_TOTAL_CONSIDERED = "total_considered"
        const val KEY_REMAINING = "remaining"
        private const val BATCH_GROUPS = 100
    }
}
