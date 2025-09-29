package com.example.kwh.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterDao {
    @Transaction
    @Query("SELECT * FROM meters ORDER BY name ASC")
    fun observeMetersWithReadings(): Flow<List<MeterWithReadings>>

    @Query("SELECT * FROM meters WHERE id = :meterId")
    suspend fun getMeterById(meterId: Long): MeterEntity?

    @Query("SELECT * FROM meter_readings WHERE meter_id = :meterId ORDER BY recorded_at DESC")
    fun observeReadingsForMeter(meterId: Long): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE id = :readingId")
    suspend fun getReadingById(readingId: Long): MeterReadingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeter(meter: MeterEntity): Long

    @Update
    suspend fun updateMeter(meter: MeterEntity)

    @Delete
    suspend fun deleteMeter(meter: MeterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: MeterReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<MeterReadingEntity>)

    @Query("DELETE FROM meter_readings WHERE id = :readingId")
    suspend fun deleteReadingById(readingId: Long)
}
