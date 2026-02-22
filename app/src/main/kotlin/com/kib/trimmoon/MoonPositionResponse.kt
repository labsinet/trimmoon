// MoonPositionResponse.kt
package com.kib.trimmoon

data class MoonPositionResponse(
    val moondata: List<MoonPositionData>?
)

data class MoonPositionData(
    val time: String,
    val eclipticlon: Double?  // екліптична довгота для знака Зодіаку
)