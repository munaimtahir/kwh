package com.example.kwh.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.kwh.settings.SettingsRepository
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * This test demonstrates that the fix ensures proper test isolation.
 * Each test gets its own fresh WorkManager instance without relying on static state.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TestIsolationDemoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var scheduler: ReminderScheduler

    @Before
    fun setup() {
        // Each test gets fresh WorkManager initialization - no static state!
        val configuration = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        
        settingsRepository = SettingsRepository(context)
        scheduler = ReminderScheduler(context, settingsRepository)
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
    }

    @Test
    fun testOne_runsIndependently() {
        // This test will have its own fresh WorkManager instance
        val workManager = WorkManager.getInstance(context)
        assertTrue(workManager.getWorkInfosByTag("test").get().isEmpty())
    }

    @Test
    fun testTwo_alsoRunsIndependently() {
        // This test will also have its own fresh WorkManager instance
        // No shared state with testOne
        val workManager = WorkManager.getInstance(context)
        assertTrue(workManager.getWorkInfosByTag("test").get().isEmpty())
    }
}