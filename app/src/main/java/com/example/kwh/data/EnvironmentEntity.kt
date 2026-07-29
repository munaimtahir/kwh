package com.example.kwh.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "environments")
data class EnvironmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
