package com.example.emt.data

import kotlinx.coroutines.flow.Flow
import java.util.Date

class UsageRepository(private val usageDao: UsageDao) {

    val allUsages: Flow<List<Usage>> = usageDao.getAll()

    suspend fun insert(kwh: Double, date: Date) {
        val newUsage = Usage(kwh = kwh, date = date)
        usageDao.insert(newUsage)
    }
}
