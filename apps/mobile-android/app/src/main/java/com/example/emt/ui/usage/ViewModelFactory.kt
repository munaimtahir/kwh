package com.example.emt.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.emt.data.UsageRepository

class ViewModelFactory(private val usageRepository: UsageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UsageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UsageViewModel(usageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
