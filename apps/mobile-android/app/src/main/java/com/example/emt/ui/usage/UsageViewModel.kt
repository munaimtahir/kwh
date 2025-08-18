package com.example.emt.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emt.data.Usage
import com.example.emt.data.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

class UsageViewModel(private val repository: UsageRepository) : ViewModel() {

    val allUsages: StateFlow<List<Usage>> = repository.allUsages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addUsage(kwh: Double, date: Date) {
        viewModelScope.launch {
            repository.insert(kwh, date)
        }
    }
}
