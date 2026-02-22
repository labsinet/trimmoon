package com.kib.trimmoon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DataLoader(private val dao: MoonDao) {
    private val api: MoonApiService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://aa.usno.navy.mil/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(MoonApiService::class.java)
    }

    suspend fun loadDataForYear(year: Int) = withContext(Dispatchers.IO) {  // без apiKey
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var date = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        while (!date.isAfter(endDate)) {
            val dateStr = date.format(formatter)
            if (dao.getByDate(dateStr) == null) {
                try {
                    val phaseCall = api.getMoonPhase(dateStr)
                    val positionCall = api.getMoonPosition(dateStr)

                    val phaseResponse = phaseCall.execute().body()
                    val positionResponse = positionCall.execute().body()

                    if (phaseResponse != null && positionResponse != null) {
                        val lunarAge = calculateLunarAge(phaseResponse.closestphase?.date)  // вік від New Moon
                        val lunarDay = (lunarAge.toInt() + 1).coerceIn(1, 30)

                        val zodiacSign = calculateZodiacSign(positionResponse.moondata?.firstOrNull()?.eclipticlon ?: 0.0)

                        val isWaxing = phaseResponse.curphase?.contains("Waxing") ?: false

                        val illumination = phaseResponse.fracillum?.replace("%", "")?.toDoubleOrNull() ?: 0.0

                        val status = calculateStatus(
                            phaseResponse.curphase ?: "",
                            isWaxing,
                            lunarDay,
                            zodiacSign,
                            date.dayOfWeek.value
                        )

                        val info = MoonInfo(
                            dateStr,
                            phaseResponse.curphase ?: "",
                            isWaxing,
                            illumination,
                            lunarDay,
                            zodiacSign,
                            status
                        )

                        dao.insert(info)
                    }
                } catch (e: Exception) {
                    // Лог помилок
                }
            }
            date = date.plusDays(1)
        }
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
        lunarDay: Int,          // змінив на Int, бо lunarDay — це число
        zodiacSign: String,
        weekday: Int
    ): Int {
        // Ваш попередній код розрахунку score
        val favorableSigns = setOf("Leo", "Virgo", "Taurus", "Capricorn", "Libra")
        val unfavorableSigns = setOf("Cancer", "Pisces", "Scorpio", "Aries", "Aquarius")
        val favorableLunarDays = setOf(5,6,8,11,13,14,19,21,22,27,28)
        val satanicDays = setOf(9,15,23,29)

        val signScore = when {
            favorableSigns.contains(zodiacSign) -> 2
            unfavorableSigns.contains(zodiacSign) -> -2
            else -> 0
        }

        val phaseScore = when {
            phaseName.contains("New", ignoreCase = true) || phaseName.contains("Full", ignoreCase = true) -> -2
            isWaxing -> 1
            else -> 0
        }

        val lunarScore = when {
            satanicDays.contains(lunarDay) -> -3
            !favorableLunarDays.contains(lunarDay) -> -1
            favorableLunarDays.contains(lunarDay) -> 2
            else -> 0
        }

        val weekdayScore = if (weekday == 7) -2 else if (weekday == 4 || weekday == 6) 1 else 0

        val totalScore = signScore + phaseScore + lunarScore + weekdayScore

        return when {
            totalScore >= 3 -> 1
            totalScore <= -2 -> -1
            else -> 0
        }
    }

    private fun convertUsnoData(usnoData: MoonPhaseResponse, date: LocalDate): com.kib.trimmoon.MoonData {
        // Якщо структура відповіді USNO інша — адаптуйте поля відповідно
        val curphase = usnoData.curphase ?: "Unknown"
        val fracillumStr = usnoData.fracillum ?: "0%"

        // Конвертуємо відсоток освітлення з рядка у число
        val illuminationPercent = fracillumStr.removeSuffix("%").toDoubleOrNull() ?: 0.0
        val illumination = illuminationPercent / 100.0

        // Визначаємо, чи місяць росте чи спадає на основі фази
        val isWaxing = when (curphase.lowercase()) {
            "new moon" -> true
            "waxing crescent" -> true
            "first quarter" -> true
            "waxing gibbous" -> true
            "full moon" -> false
            "waning gibbous" -> false
            "last quarter" -> false
            "waning crescent" -> false
            else -> true  // fallback
        }

        // Орієнтовний lunar_age на основі фази (можна покращити з closestphase)
        val lunarAge = when (curphase.lowercase()) {
            "new moon" -> 0.0
            "waxing crescent" -> 3.0
            "first quarter" -> 7.5
            "waxing gibbous" -> 12.0
            "full moon" -> 14.5
            "waning gibbous" -> 17.0
            "last quarter" -> 22.0
            "waning crescent" -> 26.0
            else -> 7.0  // середнє
        }

        // Знак зодіаку — якщо у вас є MoonPositionResponse, візьміть eclipticlon
        // Якщо тільки MoonPhaseResponse — використовуйте наближення або окремий запит
        val zodiacSign = getZodiacSign(date)  // ваша функція

        return com.kib.trimmoon.MoonData(
            phase_name = curphase,
            is_waxing = isWaxing,
            illumination = illumination,
            lunar_age = lunarAge,
            zodiac_sign = zodiacSign
        )
    }

    private fun getZodiacSign(date: LocalDate): String {
        val day = date.dayOfMonth
        val month = date.monthValue

        return when {
            (month == 1 && day >= 20) || (month == 2 && day <= 18) -> "Aquarius"
            (month == 2 && day >= 19) || (month == 3 && day <= 20) -> "Pisces"
            (month == 3 && day >= 21) || (month == 4 && day <= 19) -> "Aries"
            (month == 4 && day >= 20) || (month == 5 && day <= 20) -> "Taurus"
            (month == 5 && day >= 21) || (month == 6 && day <= 20) -> "Gemini"
            (month == 6 && day >= 21) || (month == 7 && day <= 22) -> "Cancer"
            (month == 7 && day >= 23) || (month == 8 && day <= 22) -> "Leo"
            (month == 8 && day >= 23) || (month == 9 && day <= 22) -> "Virgo"
            (month == 9 && day >= 23) || (month == 10 && day <= 22) -> "Libra"
            (month == 10 && day >= 23) || (month == 11 && day <= 21) -> "Scorpio"
            (month == 11 && day >= 22) || (month == 12 && day <= 21) -> "Sagittarius"
            (month == 12 && day >= 22) || (month == 1 && day <= 19) -> "Capricorn"
            else -> "Unknown"
        }
    }
}



