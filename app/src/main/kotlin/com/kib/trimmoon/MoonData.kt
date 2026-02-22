package com.kib.trimmoon

// MoonData.kt - модель для US Naval Observatory API
data class MoonData(
    val properties: MoonProperties
)

data class MoonProperties(
    val data: MoonInfoData
)

data class MoonInfoData(
    val curphase: String,        // Поточна фаза місяця
    val fracillum: String,       // Відсоток освітлення (наприклад "78%")
    val closestphase: ClosestPhase
)

data class ClosestPhase(
    val phase: String,           // Назва фази (Full Moon, New Moon, etc.)
    val date: String,            // Дата фази
    val time: String             // Час фази
)
