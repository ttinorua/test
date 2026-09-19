package com.financetracker.app

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.financetracker.app.data.db.AppDatabase
import com.financetracker.app.data.enablebanking.EnableBankingSyncWorker
import com.financetracker.app.data.prefs.AiInsightsCache
import com.financetracker.app.data.prefs.BudgetLimits
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.repository.FinanceRepository

class FinanceApp : Application() {

    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CurrencySettings.init(this)
        BudgetSettings.init(this)
        BudgetLimits.init(this)
        AiInsightsCache.init(this)
        EnableBankingPrefs.init(this)
        repository = FinanceRepository(AppDatabase.getInstance(this))

        // Best-effort sync once per app launch; a no-op inside the worker if not connected.
        // Runs as WorkManager-managed work (see EnableBankingSyncWorker) rather than a plain
        // coroutine, so a sync that can take a long time (the very first sync, or any sync after
        // a reinstall wiped local data) survives the app being backgrounded or its process being
        // killed instead of silently dying mid-way. KEEP: if a previous launch's sync is still
        // running, this one shouldn't interrupt it — the user can always see current status and
        // force a fresh run via Settings > Bank > Sync now.
        WorkManager.getInstance(this).enqueueUniqueWork(
            EnableBankingSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            EnableBankingSyncWorker.buildRequest()
        )
    }
}
