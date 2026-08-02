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

    @Query(
        "SELECT * FROM meter_readings WHERE meter_id = :meterId AND recorded_at < :start ORDER BY recorded_at DESC LIMIT 1"
    )
    suspend fun getLatestReadingBefore(meterId: Long, start: Long): MeterReadingEntity?

    @Query(
        "SELECT * FROM meter_readings WHERE meter_id = :meterId AND recorded_at >= :start AND recorded_at < :end ORDER BY recorded_at ASC LIMIT 1"
    )
    suspend fun getEarliestReadingInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity?

    @Query(
        "SELECT * FROM meter_readings WHERE meter_id = :meterId AND recorded_at >= :start AND recorded_at < :end ORDER BY recorded_at DESC LIMIT 1"
    )
    suspend fun getLatestReadingInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity?

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

    @Query(
        "SELECT * FROM meter_readings WHERE meter_id = :meterId AND recorded_at < :start " +
            "ORDER BY recorded_at DESC LIMIT 1"
    )
    suspend fun latestBefore(meterId: Long, start: Long): MeterReadingEntity?

    @Query(
        "SELECT * FROM meter_readings WHERE meter_id = :meterId AND recorded_at >= :start " +
            "AND recorded_at < :end ORDER BY recorded_at ASC LIMIT 1"
    )
    suspend fun earliestInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity?

    @Query(
        "SELECT * FROM meter_readings WHERE meter_id = :meterId AND recorded_at >= :start " +
            "AND recorded_at < :end ORDER BY recorded_at DESC LIMIT 1"
    )
    suspend fun latestInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity?

    // --- Environments ---

    @Query("SELECT * FROM environments ORDER BY name ASC")
    fun observeEnvironments(): Flow<List<EnvironmentEntity>>

    @Query("SELECT * FROM environments WHERE id = :environmentId")
    suspend fun getEnvironmentById(environmentId: Long): EnvironmentEntity?

    @Query("SELECT * FROM meters WHERE environment_id = :environmentId")
    suspend fun getMetersForEnvironment(environmentId: Long): List<MeterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnvironment(environment: EnvironmentEntity): Long

    @Update
    suspend fun updateEnvironment(environment: EnvironmentEntity)

    @Delete
    suspend fun deleteEnvironment(environment: EnvironmentEntity)

    // --- Meter activations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivation(activation: MeterActivationEntity): Long

    @Update
    suspend fun updateActivation(activation: MeterActivationEntity)

    @Query(
        "SELECT * FROM meter_activations WHERE meter_id = :meterId AND ended_at IS NULL " +
            "ORDER BY started_at DESC LIMIT 1"
    )
    suspend fun getActiveActivationForMeter(meterId: Long): MeterActivationEntity?

    @Query(
        "SELECT * FROM meter_activations WHERE meter_id IN " +
            "(SELECT id FROM meters WHERE environment_id = :environmentId) " +
            "AND ended_at IS NULL ORDER BY started_at DESC LIMIT 1"
    )
    suspend fun getActiveActivationForEnvironment(environmentId: Long): MeterActivationEntity?

    @Query(
        "SELECT * FROM meters WHERE environment_id = :environmentId AND id IN " +
            "(SELECT meter_id FROM meter_activations WHERE ended_at IS NULL) LIMIT 1"
    )
    suspend fun getActiveMeterForEnvironment(environmentId: Long): MeterEntity?

    @Query(
        "SELECT * FROM meter_activations WHERE meter_id IN " +
            "(SELECT id FROM meters WHERE environment_id = :environmentId) AND ended_at IS NULL"
    )
    suspend fun getOpenActivationsForEnvironment(environmentId: Long): List<MeterActivationEntity>

    @Query("SELECT * FROM meter_activations WHERE ended_at IS NULL")
    fun observeActiveActivations(): Flow<List<MeterActivationEntity>>

    @Query("SELECT * FROM meter_activations")
    fun observeAllActivations(): Flow<List<MeterActivationEntity>>

    @Query("SELECT * FROM meter_activations WHERE meter_id = :meterId")
    suspend fun getActivationsForMeter(meterId: Long): List<MeterActivationEntity>

    @Query("SELECT * FROM meter_activations WHERE meter_id = :meterId ORDER BY started_at DESC")
    fun observeActivationHistory(meterId: Long): Flow<List<MeterActivationEntity>>

    // --- Switch outcomes (adaptive calibration, M9) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwitchOutcome(outcome: SwitchOutcomeEntity): Long

    @Query(
        "SELECT * FROM switch_outcomes WHERE meter_id = :meterId " +
            "ORDER BY actual_switch_date DESC LIMIT :limit"
    )
    suspend fun getRecentSwitchOutcomes(meterId: Long, limit: Int): List<SwitchOutcomeEntity>

    @Query("SELECT * FROM switch_outcomes WHERE meter_id = :meterId ORDER BY actual_switch_date DESC")
    fun observeSwitchOutcomes(meterId: Long): Flow<List<SwitchOutcomeEntity>>
}
