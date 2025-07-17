package com.yourcompany.electrictracker.data.repository

import com.yourcompany.electrictracker.data.dao.UsageDao
import com.yourcompany.electrictracker.data.entities.Usage
import kotlinx.coroutines.flow.Flow

class UsageRepository(private val usageDao: UsageDao) {
    val allUsage: Flow<List<Usage>> = usageDao.getAllUsage()

    suspend fun insert(usage: Usage) {
        usageDao.insert(usage)
    }
}
