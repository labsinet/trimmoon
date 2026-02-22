package com.kib.trimmoon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DataLoader(private val dao: MoonDao) {
    private val api: MoonApiService

    init {
        val client = OkHttpClient.Builder()
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://aa.usno.navy.mil/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(MoonApiService::class.java)
    }

    suspend fun loadDataForYear(year: Int) = withContext(Dispatchers.IO) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var date = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        var processedCount = 0
        while (!date.isAfter(endDate)) {
            val dateStr = date.format(formatter)
            val existingData = dao.getByDate(dateStr)
            if (existingData == null) {
                try {
                    // Make real API call
                    val response = api.getMoonData(dateStr).execute()
                    if (response.isSuccessful) {
                        val moonData = response.body()
                        if (moonData != null) {
                            // Конвертуємо дані з USNO API у наш формат
                            val convertedData = convertUsnoData(moonData, date)
                            val lunarDay = (convertedData.lunar_age.toInt() + 1).coerceIn(1, 30)
                            val status = calculateStatus(convertedData, lunarDay, date.dayOfWeek.value)
                            val info = MoonInfo(
                                dateStr,
                                convertedData.phase_name,
                                convertedData.is_waxing,
                                convertedData.illumination,
                                lunarDay,
                                convertedData.zodiac_sign,
                                status
                            )
                            dao.insert(info)
                            processedCount++
                        }
                    } else {
                        // Skip failed requests to avoid spam
                        println("API failed for $dateStr: ${response.code()}")
                    }
                } catch (e: Exception) {
                    // Skip failed requests
                    println("Exception for $dateStr: ${e.message}")
                }
            }
            date = date.plusDays(1)

            // Limit processing to avoid too much processing at once
            if (processedCount >= 30) {
                break
            }
        }
    }

    private fun calculateStatus(data: MoonData, lunarDay: Int, weekday: Int): Int {
        // Детальні правила Вронського

        // Знаки
        val favorableSigns = setOf("Leo", "Virgo", "Taurus", "Capricorn", "Libra")
        val unfavorableSigns = setOf("Cancer", "Pisces", "Scorpio", "Aries", "Aquarius")
        val signScore = when {
            favorableSigns.contains(data.zodiac_sign) -> 2
            unfavorableSigns.contains(data.zodiac_sign) -> -2
            else -> 0
        }

        // Фази (припустимо для росту - waxing good; для зміцнення - waning good, але тут для росту)
        val phaseScore = when {
            data.phase_name == "New Moon" || data.phase_name == "Full Moon" -> -2
            data.is_waxing -> 1
            else -> 0  // waning neutral for growth, but can be good for strength
        }

        // Лунні дні
        val favorableLunarDays = setOf(5,6,8,11,13,14,19,21,22,27,28)
        val unfavorableLunarDays = setOf(1,2,3,4,7,10,12,16,17,18,20,24,25,26,30)
        val satanicDays = setOf(9,15,23,29) // Найгірші
        val lunarScore = when {
            satanicDays.contains(lunarDay) -> -3
            unfavorableLunarDays.contains(lunarDay) -> -1
            favorableLunarDays.contains(lunarDay) -> 2
            else -> 0
        }

        // Дні тижня (1=Monday, 7=Sunday)
        val favorableWeekdays = setOf(1,2,3,4,5,6) // Понеділок-субота з варіаціями, але sunday bad
        val weekdayScore = if (weekday == 7) -2 else if (setOf(4,6).contains(weekday)) 1 else 0 // Четвер/субота bonus

        // Загальний score
        val totalScore = signScore + phaseScore + lunarScore + weekdayScore

        return when {
            totalScore >= 3 -> 1   // Positive
            totalScore <= -2 -> -1 // Negative
            else -> 0              // Neutral
        }
    }

    private fun convertUsnoData(usnoData: MoonData, date: LocalDate): com.kib.trimmoon.MoonData {
        val moonInfo = usnoData.properties.data

        // Конвертуємо відсоток освітлення з рядка у число
        val illuminationPercent = moonInfo.fracillum.removeSuffix("%").toDoubleOrNull() ?: 0.0
        val illumination = illuminationPercent / 100.0

        // Визначаємо, чи місяць росте чи спадає на основі фази
        val isWaxing = when (moonInfo.curphase.lowercase()) {
            "new moon" -> true
            "waxing crescent" -> true
            "first quarter" -> true
            "waxing gibbous" -> true
            "full moon" -> false
            "waning gibbous" -> false
            "last quarter" -> false
            "waning crescent" -> false
            else -> true
        }

        // Оцінюємо lunar_age на основі відсотка освітлення та фази
        val lunarAge = when (moonInfo.curphase.lowercase()) {
            "new moon" -> 0.0
            "waxing crescent" -> 3.0
            "first quarter" -> 7.5
            "waxing gibbous" -> 12.0
            "full moon" -> 14.5
            "waning gibbous" -> 17.0
            "last quarter" -> 22.0
            "waning crescent" -> 26.0
            else -> 7.0
        }

        // Визначаємо знак зодіаку на основі дати
        val zodiacSign = getZodiacSign(date)

        return com.kib.trimmoon.MoonData(
            phase_name = moonInfo.curphase,
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



