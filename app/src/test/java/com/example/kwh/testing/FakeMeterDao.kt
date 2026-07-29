package com.example.kwh.testing

import com.example.kwh.data.EnvironmentEntity
import com.example.kwh.data.MeterActivationEntity
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
    private val environments = mutableListOf<EnvironmentEntity>()
    private val activations = mutableListOf<MeterActivationEntity>()
    private val updateSignal = MutableStateFlow(0L)
    private var nextMeterId = 1L
    private var nextReadingId = 1L
    private var nextEnvironmentId = 1L
    private var nextActivationId = 1L

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

    override suspend fun latestBefore(meterId: Long, start: Long): MeterReadingEntity? {
        return getLatestReadingBefore(meterId, start)
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

    override suspend fun earliestInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity? {
        return getEarliestReadingInWindow(meterId, start, end)
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

    override suspend fun latestInWindow(
        meterId: Long,
        start: Long,
        end: Long
    ): MeterReadingEntity? {
        return getLatestReadingInWindow(meterId, start, end)
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

    override fun observeEnvironments(): Flow<List<EnvironmentEntity>> {
        return updateSignal.map { environments.sortedBy { env -> env.name.lowercase() } }
    }

    override suspend fun getEnvironmentById(environmentId: Long): EnvironmentEntity? {
        return environments.find { it.id == environmentId }
    }

    override suspend fun insertEnvironment(environment: EnvironmentEntity): Long {
        val assignedId = if (environment.id == 0L) nextEnvironmentId++ else environment.id
        val entity = environment.copy(id = assignedId)
        environments.removeAll { it.id == assignedId }
        environments.add(entity)
        notifyObservers()
        return assignedId
    }

    override suspend fun updateEnvironment(environment: EnvironmentEntity) {
        val index = environments.indexOfFirst { it.id == environment.id }
        if (index != -1) {
            environments[index] = environment
            notifyObservers()
        }
    }

    override suspend fun deleteEnvironment(environment: EnvironmentEntity) {
        val removed = environments.removeIf { it.id == environment.id }
        if (removed) {
            meters.replaceAll {
                if (it.environmentId == environment.id) it.copy(environmentId = null) else it
            }
            notifyObservers()
        }
    }

    override suspend fun insertActivation(activation: MeterActivationEntity): Long {
        val assignedId = if (activation.id == 0L) nextActivationId++ else activation.id
        val entity = activation.copy(id = assignedId)
        activations.removeAll { it.id == assignedId }
        activations.add(entity)
        notifyObservers()
        return assignedId
    }

    override suspend fun updateActivation(activation: MeterActivationEntity) {
        val index = activations.indexOfFirst { it.id == activation.id }
        if (index != -1) {
            activations[index] = activation
            notifyObservers()
        }
    }

    override suspend fun getActiveActivationForMeter(meterId: Long): MeterActivationEntity? {
        return activations
            .filter { it.meterId == meterId && it.endedAt == null }
            .maxByOrNull { it.startedAt }
    }

    override suspend fun getActiveActivationForEnvironment(
        environmentId: Long
    ): MeterActivationEntity? {
        val meterIds = meters.filter { it.environmentId == environmentId }.map { it.id }.toSet()
        return activations
            .filter { it.meterId in meterIds && it.endedAt == null }
            .maxByOrNull { it.startedAt }
    }

    override suspend fun getActiveMeterForEnvironment(environmentId: Long): MeterEntity? {
        val activeMeterIds = activations.filter { it.endedAt == null }.map { it.meterId }.toSet()
        return meters.find { it.environmentId == environmentId && it.id in activeMeterIds }
    }

    override fun observeActivationHistory(meterId: Long): Flow<List<MeterActivationEntity>> {
        return updateSignal.map {
            activations.filter { it.meterId == meterId }.sortedByDescending { a -> a.startedAt }
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
