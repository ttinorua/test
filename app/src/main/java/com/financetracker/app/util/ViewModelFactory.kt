package com.financetracker.app.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Minimal factory for constructing ViewModels with hand-wired dependencies (no DI framework). */
class ViewModelFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
