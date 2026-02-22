package com.kib.trimmoon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.cos

class DataLoader(private val dao: MoonDao) {
    private val api: MoonApiService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://aa.usno.navy.mil/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(MoonApiService::class.java)
    }

    suspend fun loadDataForYear(year: Int) = withContext(Dispatchers.IO) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var date = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        while (!date.isAfter(endDate)) {
            loadDataForDate(date)
            date = date.plusDays(1)
        }
    }

    suspend fun loadDataForDate(date: LocalDate) {
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        if (dao.getByDate(dateStr) == null) {
            try {
                println("DataLoader: Calculating moon data for $dateStr")

                // Calculate moon data using astronomical formulas
                val moonData = calculateMoonData(date)

                val status = calculateStatus(
                    moonData.phaseName,
                    moonData.isWaxing,
                    moonData.lunarDay,
                    moonData.zodiacSign,
                    date.dayOfWeek.value
                )

                val info = MoonInfo(
                    dateStr,
                    moonData.phaseName,
                    moonData.isWaxing,
                    moonData.illumination,
                    moonData.lunarDay,
                    moonData.zodiacSign,
                    status
                )

                dao.insert(info)
                println("DataLoader: Successfully calculated and inserted data for $dateStr")

            } catch (e: Exception) {
                println("DataLoader: Exception calculating data for $dateStr: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println("DataLoader: Data already exists for $dateStr")
        }
    }

    private data class CalculatedMoonData(
        val phaseName: String,
        val isWaxing: Boolean,
        val illumination: Double,
        val lunarDay: Int,
        val zodiacSign: String
    )

    private fun calculateMoonData(date: LocalDate): CalculatedMoonData {
        // Calculate Julian Day
        val a = (14 - date.monthValue) / 12
        val y = date.year + 4800 - a
        val m = date.monthValue + 12 * a - 3
        val julianDay = date.dayOfMonth + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045

        // Reference: January 1, 2000, 12:00 UT
        val jd2000 = 2451545.0
        val daysSince2000 = julianDay - jd2000

        // Moon's mean longitude
        val moonMeanLongitude = (218.3164477 + 13.176396464 * daysSince2000) % 360

        // Moon's mean anomaly
        val moonMeanAnomaly = (134.9633964 + 13.064992950 * daysSince2000) % 360

        // Sun's mean anomaly
        val sunMeanAnomaly = (357.5291092 + 0.985600281 * daysSince2000) % 360

        // Moon's phase angle
        val phaseAngle = moonMeanLongitude - sunMeanAnomaly

        // Moon's illuminated fraction (simplified)
        val illumination = (1 + cos(Math.toRadians(phaseAngle))) / 2

        // Determine moon phase
        val phaseDegrees = (phaseAngle % 360 + 360) % 360
        val phaseName = when {
            phaseDegrees < 22.5 -> "New Moon"
            phaseDegrees < 67.5 -> "Waxing Crescent"
            phaseDegrees < 112.5 -> "First Quarter"
            phaseDegrees < 157.5 -> "Waxing Gibbous"
            phaseDegrees < 202.5 -> "Full Moon"
            phaseDegrees < 247.5 -> "Waning Gibbous"
            phaseDegrees < 292.5 -> "Last Quarter"
            phaseDegrees < 337.5 -> "Waning Crescent"
            else -> "New Moon"
        }

        val isWaxing = phaseName.contains("Waxing") || phaseName == "New Moon"

        // Calculate lunar day (simplified - actual calculation is more complex)
        val lunarCycleDays = 29.530588
        val newMoonJd = 2451549.0 // Approximate JD for new moon near 2000
        val daysSinceNewMoon = (julianDay - newMoonJd) % lunarCycleDays
        val lunarDay = (daysSinceNewMoon.toInt() + 1).coerceIn(1, 30)

        // Calculate zodiac sign based on moon's ecliptic longitude (simplified)
        val zodiacSign = when ((moonMeanLongitude % 360).toInt()) {
            in 0..29 -> "Aries"
            in 30..59 -> "Taurus"
            in 60..89 -> "Gemini"
            in 90..119 -> "Cancer"
            in 120..149 -> "Leo"
            in 150..179 -> "Virgo"
            in 180..209 -> "Libra"
            in 210..239 -> "Scorpio"
            in 240..269 -> "Sagittarius"
            in 270..299 -> "Capricorn"
            in 300..329 -> "Aquarius"
            in 330..359 -> "Pisces"
            else -> "Unknown"
        }

        return CalculatedMoonData(phaseName, isWaxing, illumination, lunarDay, zodiacSign)
    }

    private fun calculateLunarAge(newMoonDate: String?): Double {
        if (newMoonDate.isNullOrEmpty()) return 0.0
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val newMoon = LocalDate.parse(newMoonDate, formatter)
        return ChronoUnit.DAYS.between(newMoon, LocalDate.now()).toDouble()
    }

    private fun calculateZodiacSign(eclipticLon: Double): String {
        return when {
            eclipticLon < 30 -> "Aries"
            eclipticLon < 60 -> "Taurus"
            eclipticLon < 90 -> "Gemini"
            eclipticLon < 120 -> "Cancer"
            eclipticLon < 150 -> "Leo"
            eclipticLon < 180 -> "Virgo"
            eclipticLon < 210 -> "Libra"
            eclipticLon < 240 -> "Scorpio"
            eclipticLon < 270 -> "Sagittarius"
            eclipticLon < 300 -> "Capricorn"
            eclipticLon < 330 -> "Aquarius"
            else -> "Pisces"
        }
    }

    private fun calculateStatus(
        phaseName: String,
        isWaxing: Boolean,
        lunarDay: Int,
        zodiacSign: String,
        weekday: Int
    ): Int {
        val favorableSigns = setOf("Leo", "Virgo", "Taurus", "Capricorn", "Libra")
        val unfavorableSigns = setOf("Cancer", "Pisces", "Scorpio", "Aries", "Aquarius")
        val favorableLunarDays = setOf(5,6,8,11,13,14,19,21,22,27,28)
        val satanicDays = setOf(9,15,23,29)

        val signScore = when {
            favorableSigns.contains(zodiacSign) -> 2
            unfavorableSigns.contains(zodiacSign) -> -1
            else -> 1
        }

        val phaseScore = when {
            phaseName.contains("New", ignoreCase = true) || phaseName.contains("Full", ignoreCase = true) -> -1
            isWaxing -> 1
            else -> 0
        }

        val lunarScore = when {
            satanicDays.contains(lunarDay) -> -2
            favorableLunarDays.contains(lunarDay) -> 2
            else -> 0
        }

        val weekdayScore = if (weekday == 7) -1 else if (weekday == 4 || weekday == 6) 1 else 0

        val totalScore = signScore + phaseScore + lunarScore + weekdayScore

        println("Status calculation for $zodiacSign, $phaseName, day $lunarDay, weekday $weekday: sign=$signScore, phase=$phaseScore, lunar=$lunarScore, weekday=$weekdayScore, total=$totalScore")

        return when {
            totalScore >= 2 -> 1  // Зменшив поріг для позитивних днів
            totalScore <= -2 -> -1
            else -> 0
        }
    }
}
