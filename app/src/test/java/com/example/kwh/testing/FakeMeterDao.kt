package com.example.kwh.testing

import com.example.kwh.data.MeterDao
import com.example.kwh.data.MeterEntity
import com.example.kwh.data.MeterReadingEntity
import com.example.kwh.data.MeterWithReadings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Lightweight in-memory implementation of [MeterDao] for JVM unit tests.
 */
class FakeMeterDao : MeterDao {
    private val meters = mutableListOf<MeterEntity>()
    private val readings = mutableListOf<MeterReadingEntity>()
    private val updateSignal = MutableStateFlow(0L)
    private var nextMeterId = 1L
    private var nextReadingId = 1L

    override fun observeMetersWithReadings(): Flow<List<MeterWithReadings>> {
        return updateSignal.map { buildMetersWithReadings() }
    }

    override suspend fun getMeterById(meterId: Long): MeterEntity? {
        return meters.find { it.id == meterId }
    }

    override fun observeReadingsForMeter(meterId: Long): Flow<List<MeterReadingEntity>> {
        return updateSignal.map { currentReadingsForMeter(meterId) }
    }

    override suspend fun getReadingById(readingId: Long): MeterReadingEntity? {
        return readings.find { it.id == readingId }
    }

    override suspend fun getLatestReadingBefore(meterId: Long, start: Long): MeterReadingEntity? {
        return readings
            .filter { it.meterId == meterId && it.recordedAt < start }
            .maxByOrNull { it.recordedAt }
    }

    override suspend fun getEarliestReadingInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity? {
        return readings
            .filter { it.meterId == meterId && it.recordedAt in start until end }
            .minByOrNull { it.recordedAt }
    }

    override suspend fun getLatestReadingInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity? {
        return readings
            .filter { it.meterId == meterId && it.recordedAt in start until end }
            .maxByOrNull { it.recordedAt }
    }

    override suspend fun insertMeter(meter: MeterEntity): Long {
        val assignedId = if (meter.id == 0L) nextMeterId++ else meter.id
        val entity = meter.copy(id = assignedId)
        meters.removeAll { it.id == assignedId }
        meters.add(entity)
        notifyObservers()
        return assignedId
    }

    override suspend fun updateMeter(meter: MeterEntity) {
        val index = meters.indexOfFirst { it.id == meter.id }
        if (index != -1) {
            meters[index] = meter
            notifyObservers()
        }
    }

    override suspend fun deleteMeter(meter: MeterEntity) {
        val removed = meters.removeIf { it.id == meter.id }
        if (removed) {
            readings.removeAll { it.meterId == meter.id }
            notifyObservers()
        }
    }

    override suspend fun insertReading(reading: MeterReadingEntity): Long {
        val assignedId = if (reading.id == 0L) nextReadingId++ else reading.id
        val entity = reading.copy(id = assignedId)
        readings.removeAll { it.id == assignedId }
        readings.add(entity)
        notifyObservers()
        return assignedId
    }

    override suspend fun insertReadings(readings: List<MeterReadingEntity>) {
        readings.forEach { insertReading(it) }
    }

    override suspend fun deleteReadingById(readingId: Long) {
        val removed = this.readings.removeIf { it.id == readingId }
        if (removed) {
            notifyObservers()
        }
    }

    private fun buildMetersWithReadings(): List<MeterWithReadings> {
        return meters
            .sortedBy { it.name.lowercase() }
            .map { meter ->
                MeterWithReadings(
                    meter = meter,
                    readings = readings
                        .filter { it.meterId == meter.id }
                        .sortedByDescending { it.recordedAt }
                )
            }
    }

    private fun currentReadingsForMeter(meterId: Long): List<MeterReadingEntity> {
        return readings
            .filter { it.meterId == meterId }
            .sortedByDescending { it.recordedAt }
    }

    private fun notifyObservers() {
        updateSignal.value = updateSignal.value + 1
    }
}
