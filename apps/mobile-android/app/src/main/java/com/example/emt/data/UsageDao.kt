package com.example.emt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(usage: Usage)

    @Query("SELECT * FROM usage ORDER BY date DESC")
    fun getAll(): Flow<List<Usage>>
}
