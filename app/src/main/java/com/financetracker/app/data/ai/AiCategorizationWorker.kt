package com.financetracker.app.data.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.financetracker.app.FinanceApp

/**
 * Runs [AiCategorizationCoordinator]'s backfill as WorkManager-managed background work instead
 * of a plain ViewModel coroutine, so a run that can take a long time (thousands of AI calls for
 * a real transaction history) survives navigating away from Settings, the screen turning off, or
 * the app being backgrounded — not just whether the Settings screen happens to stay open.
 */
class AiCategorizationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as FinanceApp).repository
        val outcome = AiCategorizationCoordinator.categorizeUncategorized(repository) { progress ->
            setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
        }
        val output = workDataOf(
            KEY_CATEGORIZED to outcome.categorizedCount,
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
    }
}
