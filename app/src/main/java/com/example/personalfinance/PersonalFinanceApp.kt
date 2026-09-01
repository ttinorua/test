package com.example.personalfinance

import android.app.Application
import com.example.personalfinance.data.AppDatabase
import com.example.personalfinance.data.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PersonalFinanceApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())

    val repository: FinanceRepository by lazy {
        val database = AppDatabase.getInstance(this)
        FinanceRepository(database.transactionDao(), database.categoryDao())
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.ensureDefaultCategories()
        }
    }
}
