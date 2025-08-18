package com.example.emt

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.emt.data.AppDatabase
import com.example.emt.data.Usage
import com.example.emt.data.UsageDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Date

@RunWith(AndroidJUnit4::class)
class UsageDaoTest {

    private lateinit var usageDao: UsageDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java).build()
        usageDao = db.usageDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetUsage() = runBlocking {
        val usage1 = Usage(id = 1, date = Date(1000), kwh = 10.0)
        val usage2 = Usage(id = 2, date = Date(2000), kwh = 20.0)
        usageDao.insert(usage1)
        usageDao.insert(usage2)
        val allUsages = usageDao.getAll().first()
        assertEquals(allUsages[0], usage2) // Should be sorted descending
        assertEquals(allUsages[1], usage1)
    }
}
