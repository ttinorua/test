package com.financetracker.app

import android.app.Application
import com.financetracker.app.data.db.AppDatabase
import com.financetracker.app.data.prefs.BudgetSettings
import com.financetracker.app.data.prefs.CurrencySettings
import com.financetracker.app.data.repository.FinanceRepository

class FinanceApp : Application() {

    lateinit var repository: FinanceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CurrencySettings.init(this)
        BudgetSettings.init(this)
        repository = FinanceRepository(AppDatabase.getInstance(this))
    }
}
