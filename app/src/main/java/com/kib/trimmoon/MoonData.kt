// MoonData.kt (Модель даних з API, адаптувати за потребою)
data class MoonData(
    val phase_name: String,
    val is_waxing: Boolean,
    val illumination: Double,
    val lunar_age: Double, // Дні від new moon
    val zodiac_sign: String
)
