package com.kib.trimmoon

import MoonApiService
import MoonDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DataLoader(private val dao: MoonDao) {
    private val api: MoonApiService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://moon-phase.p.rapidapi.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(MoonApiService::class.java)
    }

    suspend fun loadDataForYear(year: Int, apiKey: String) = withContext(Dispatchers.IO) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var date = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        while (!date.isAfter(endDate)) {
            val dateStr = date.format(formatter)
            if (dao.getByDate(dateStr) == null) {
                try {
                    val call = api.getMoonData(dateStr)
                    call.request().newBuilder().addHeader("X-RapidAPI-Key", apiKey).build()
                    val response = call.execute()
                    if (response.isSuccessful) {
                        val data = response.body()!!
                        val lunarDay = (data.lunar_age.toInt() + 1).coerceIn(1, 30)
                        val status = calculateStatus(data, lunarDay, date.dayOfWeek.value)
                        val info = MoonInfo(
                            dateStr,
                            data.phase_name,
                            data.is_waxing,
                            data.illumination,
                            lunarDay,
                            data.zodiac_sign,
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
}



