// MoonPhaseResponse.kt
package com.kib.trimmoon

data class MoonPhaseResponse(
    val closestphase: ClosestPhase?,
    val curphase: String?,  // поточна фаза
    val fracillum: String?  // ілюмінація %
)

data class ClosestPhase(
    val phase: String,
    val date: String,
    val time: String
)