package com.financetracker.app

import android.app.Application
import com.financetracker.app.data.db.AppDatabase
import com.financetracker.app.data.enablebanking.EnableBankingSyncCoordinator
import com.financetracker.app.data.prefs.AiInsightsCache
import com.financetracker.app.data.prefs.BudgetLimits
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.prefs.EnableBankingPrefs
import com.financetracker.app.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FinanceApp : Application() {

    lateinit var repository: FinanceRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CurrencySettings.init(this)
        BudgetSettings.init(this)
        BudgetLimits.init(this)
        AiInsightsCache.init(this)
        EnableBankingPrefs.init(this)
        repository = FinanceRepository(AppDatabase.getInstance(this))

        // Best-effort sync once per app launch; no-op if not connected. Wrapped in case of any
        // failure (network, DB) since this runs unconditionally on every cold start — it must
        // never be able to crash app launch. The user can always see current status and retry
        // via Settings > Bank > Sync now.
        applicationScope.launch {
            try {
                EnableBankingSyncCoordinator.syncSelectedAccounts(repository)
            } catch (e: Throwable) {
                // Silent: this is a background convenience sync, not a user-initiated action.
            }
        }
    }
}
