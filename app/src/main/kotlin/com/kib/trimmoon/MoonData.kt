package com.kib.trimmoon

data class MoonData(
    val phase_name: String,
    val is_waxing: Boolean,  // розрахувати з fracillum та фази
    val illumination: Double,
    val lunar_age: Double,  // розрахувати з closest New Moon
    val zodiac_sign: String  // розрахувати з eclipticlon
)