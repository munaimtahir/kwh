package com.yourcompany.electrictracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yourcompany.electrictracker.data.entities.Usage
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage ORDER BY date DESC")
    fun getAllUsage(): Flow<List<Usage>>

    @Insert
    suspend fun insert(usage: Usage)
}
