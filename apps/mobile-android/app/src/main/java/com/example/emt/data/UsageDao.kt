package com.example.emt.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(usage: Usage)

    @Update
    suspend fun update(usage: Usage)

    @Delete
    suspend fun delete(usage: Usage)

    @Query("SELECT * FROM usage ORDER BY date DESC")
    fun getAll(): Flow<List<Usage>>
}
