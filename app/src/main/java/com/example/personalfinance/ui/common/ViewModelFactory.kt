package com.example.personalfinance.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** A small manual-DI factory: each screen supplies a lambda that builds its ViewModel. */
class ViewModelFactory(
    private val creators: Map<Class<out ViewModel>, () -> ViewModel>
) : ViewModelProvider.Factory {
    constructor(vararg creators: Pair<Class<out ViewModel>, () -> ViewModel>) : this(creators.toMap())

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = creators[modelClass]
            ?: creators.entries.firstOrNull { modelClass.isAssignableFrom(it.key) }?.value
            ?: throw IllegalArgumentException("Unknown ViewModel class $modelClass")
        @Suppress("UNCHECKED_CAST")
        return creator() as T
    }
}
