package com.kib.trimmoon

import android.util.Log
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
        val a = (14 - date.monthValue) / 12
        val y = date.year + 4800 - a
        val m = date.monthValue + 12 * a - 3
        val julianDay = date.dayOfMonth + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        val d = julianDay - 2451545.0

        // 1. Сонце (середня аномалія та довгота)
        val sunMeanAnomaly = (357.529 + 0.98560028 * d) % 360
        val sunMeanLongitude = (280.459 + 0.98564736 * d) % 360

        // 2. Місяць (середня довгота та аномалія)
        val moonMeanLongitude = (218.316 + 13.176396 * d) % 360
        val moonMeanAnomaly = (134.963 + 13.064993 * d) % 360

        // --- КОРЕКЦІЯ (Рівняння центру) ---
        // Додаємо хоча б основну гармоніку, щоб Знак Зодіаку не "втікав"
        val moonCorrectedLongitude = moonMeanLongitude + 6.289 * Math.sin(Math.toRadians(moonMeanAnomaly))

        // 3. Елонгація (Кут фази)
        val elongation = (moonCorrectedLongitude - sunMeanLongitude + 360) % 360

        val isWaxing = elongation < 180
        val illumination = (1 + cos(Math.toRadians(elongation - 180))) / 2

        // Фаза
        val phaseName = when {
            elongation < 15 || elongation > 345 -> "New Moon"
            elongation < 75 -> "Waxing Crescent"
            elongation < 105 -> "First Quarter"
            elongation < 165 -> "Waxing Gibbous"
            elongation < 195 -> "Full Moon"
            elongation < 255 -> "Waning Gibbous"
            elongation < 285 -> "Last Quarter"
            else -> "Waning Crescent"
        }

        // 4. Місячний день (синхронізація з календарем)
        val knownNewMoon = 2451550.1
        var daysSinceNewMoon = (julianDay - knownNewMoon) % 29.530588
        if (daysSinceNewMoon < 0) daysSinceNewMoon += 29.530588
        val lunarDay = (daysSinceNewMoon.toInt() + 1).coerceIn(1, 30)

        // 5. Знак Зодіаку (використовуємо скориговану довготу)
        val zodiacSign = when (((moonCorrectedLongitude + 360) % 360).toInt()) {
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
            else -> "Pisces"
        }
      //  Log.d("ZodiacCheck", "$date -> $zodiacSign")
        return CalculatedMoonData(phaseName, isWaxing, illumination, lunarDay, zodiacSign)
    }

    private fun calculateStatus(
        phaseName: String,
        isWaxing: Boolean,
        lunarDay: Int,
        zodiacSign: String,
        weekday: Int
    ): Int {
        // Вронський ставить Знак на перше місце
        val bestSigns = setOf("Leo", "Virgo")
        val goodSigns = setOf("Taurus", "Capricorn", "Libra")
        val badSigns = setOf("Cancer", "Pisces", "Scorpio")
        val neutralSigns = setOf("Gemini", "Sagittarius", "Aquarius")

        val favorableDays = setOf(5, 8, 11, 13, 14, 19, 21, 22, 26, 27, 28)
        val dangerousDays = setOf(9, 15, 23, 29)

        var score = 0

        // Нарахування балів (максимально лояльне)
        if (bestSigns.contains(zodiacSign)) score += 4
        else if (goodSigns.contains(zodiacSign)) score += 3
        else if (badSigns.contains(zodiacSign)) score -= 2

        if (favorableDays.contains(lunarDay)) score += 2
        if (dangerousDays.contains(lunarDay)) score -= 2
        if (neutralSigns.contains(zodiacSign)) score += 1

        if (isWaxing) score += 1
        if (phaseName == "Full Moon") score += 1 // Повня - це "зарядка" волосся

        if (weekday == 7) score -= 2 // Неділя - однозначне "ні"
        if (weekday == 4 || weekday == 6) score += 1

        // Повертаємо 1 (позитивний), якщо набрано хоча б 3 бали
        // Тепер це реально: (Знак +2) + (День +2) - (Зменшення за щось інше) = 3+
        Log.d("ZodiacCheck", "$score -> $zodiacSign")
        return when {
            score >= 2 -> 1
            score <= -2 -> -1
            else -> 0
        }
    }

}
