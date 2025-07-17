package com.yourcompany.electrictracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "usage")
data class Usage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: Date,
    val kwh: Double
)
