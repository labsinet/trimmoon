package com.kib.trimmoon

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moon_info")
class MoonInfo(
    @PrimaryKey val date: String,
    val phase_name: String,
    val is_waxing: Boolean,
    val illumination: Double,
    val lunar_day: Int,
    val zodiac_sign: String,
    val status: Int // 1=positive, -1=negative, 0=neutral
)