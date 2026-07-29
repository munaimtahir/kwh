package com.example.kwh.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeterActivationDaoTest {

    private lateinit var database: MeterDatabase
    private lateinit var dao: MeterDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.meterDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun environmentCrud_roundTrips() = runTest {
        val envId = dao.insertEnvironment(EnvironmentEntity(name = "House"))

        val loaded = dao.getEnvironmentById(envId)
        assertNotNull(loaded)
        assertEquals("House", loaded.name)

        dao.updateEnvironment(loaded.copy(name = "Main House"))
        assertEquals("Main House", dao.getEnvironmentById(envId)?.name)

        dao.deleteEnvironment(loaded.copy(name = "Main House"))
        assertNull(dao.getEnvironmentById(envId))
    }

    @Test
    fun activation_tracksActiveMeterPerEnvironment() = runTest {
        val envId = dao.insertEnvironment(EnvironmentEntity(name = "House"))
        val meterAId = dao.insertMeter(MeterEntity(name = "Meter A", environmentId = envId))
        val meterBId = dao.insertMeter(MeterEntity(name = "Meter B", environmentId = envId))

        val activationId = dao.insertActivation(
            MeterActivationEntity(meterId = meterAId, startedAt = 1_000L)
        )

        assertEquals(meterAId, dao.getActiveMeterForEnvironment(envId)?.id)
        assertNotNull(dao.getActiveActivationForMeter(meterAId))
        assertNull(dao.getActiveActivationForMeter(meterBId))

        // Switch: close A's activation, open B's.
        val active = dao.getActiveActivationForMeter(meterAId)
        assertNotNull(active)
        dao.updateActivation(active.copy(endedAt = 2_000L))
        dao.insertActivation(MeterActivationEntity(meterId = meterBId, startedAt = 2_000L))

        assertEquals(meterBId, dao.getActiveMeterForEnvironment(envId)?.id)
        assertNull(dao.getActiveActivationForMeter(meterAId))
        assertNotNull(dao.getActiveActivationForMeter(meterBId))

        val history = dao.observeActivationHistory(meterAId).first()
        assertEquals(1, history.size)
        assertEquals(activationId, history.single().id)
    }

    @Test
    fun deleteMeter_cascadesActivations() = runTest {
        val meterId = dao.insertMeter(MeterEntity(name = "Solo"))
        dao.insertActivation(MeterActivationEntity(meterId = meterId, startedAt = 500L))

        val meter = dao.getMeterById(meterId)
        assertNotNull(meter)
        dao.deleteMeter(meter)

        assertNull(dao.getActiveActivationForMeter(meterId))
    }
}
