package com.example.kwh.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.repository.MeterRepository
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingSubmissionTest {
    @Test fun concurrentSubmissionsAreCheckedInsideRoomTransaction() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MeterDatabase::class.java).build()
        try {
            val repository = MeterRepository(db.meterDao(), Clock.systemUTC(), DefaultBillingCycleCalculator())
            val id = repository.addMeter("Home", 7, 9, 0)
            val results = (1..10).map {
                async { runCatching { repository.addReading(id, 100.0, null, 1000L + it) } }
            }.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(9, results.count { it.exceptionOrNull() is IllegalArgumentException })
            assertEquals(1, repository.readingsForMeter(id).first().size)
        } finally { db.close() }
    }
}
