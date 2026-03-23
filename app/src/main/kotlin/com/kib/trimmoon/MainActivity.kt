package com.kib.trimmoon

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.makeText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.Year
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var loader: DataLoader
    private lateinit var calendarView: MaterialCalendarView
    private lateinit var infoText: TextView
    private lateinit var yearSpinner: Spinner
    private lateinit var monthSpinner: Spinner
    private lateinit var switchReminders: SwitchCompat

    private val apiKey = "2d967e36b1msh5d742f0a8321108p1ab395jsnd2ffa9f34d4" // ← замініть на реальний ключ!

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w("MainActivity", "POST_NOTIFICATIONS permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            calendarView = findViewById(R.id.calendarView)
            infoText = findViewById(R.id.infoText)
            yearSpinner = findViewById(R.id.yearSpinner)
            monthSpinner = findViewById(R.id.monthSpinner)
            switchReminders = findViewById(R.id.switchReminders)

            db = AppDatabase.getDatabase(this)
            loader = DataLoader(db.moonDao())

            setupSpinners()

            val prefs = getSharedPreferences("trimmoon_prefs", MODE_PRIVATE)
            val remindersEnabled = prefs.getBoolean("reminders_enabled", false)
            switchReminders.isChecked = remindersEnabled

            switchReminders.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("reminders_enabled", isChecked).apply()
                if (isChecked) {
                    requestNotificationPermissionIfNeeded()
                    ReminderManager.scheduleDailyReminder(this)
                } else {
                    ReminderManager.cancelDailyReminder(this)
                }
            }

            if (remindersEnabled) {
                ReminderManager.scheduleDailyReminder(this)
            }

            NotificationHelper.createNotificationChannel(this)

            setupCalendar()
            updateCalendar()

            calendarView.setOnDateChangedListener { _, date, selected ->
                if (!selected || date == null) return@setOnDateChangedListener

                val selectedDate = LocalDate.of(date.year, date.month + 1, date.day)
                showDayInfo(selectedDate)
            }
        } catch (e: Throwable) {
            Log.e("MainCrash", "Краш на запуску", e)
            makeText(this, "Помилка: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            throw e  // щоб побачити стек-трейс
        }
    }

    private fun setupSpinners() {
        val years = (1900..2050).toList()
        yearSpinner.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, years.map { it.toString() })
        yearSpinner.setSelection(years.indexOf(Year.now().value))

        val months = (1..12).map { it.toString().padStart(2, '0') }
        monthSpinner.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, months)
        monthSpinner.setSelection(LocalDate.now().monthValue - 1)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCalendar()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        yearSpinner.onItemSelectedListener = listener
        monthSpinner.onItemSelectedListener = listener
    }

    private fun setupCalendar() {
        val calendar = Calendar.getInstance()
        val localDate = LocalDate.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        val today = LocalDate.now()
        calendarView.setCurrentDate(CalendarDay.from(today.year, today.monthValue, today.dayOfMonth))
//       calendarView.setCurrentDate(CalendarDay.from(calendar))
    }

    private fun updateCalendar() {
        val selectedYearStr = yearSpinner.selectedItem as? String ?: return
        val selectedMonthStr = monthSpinner.selectedItem as? String ?: return

        val selectedYear = selectedYearStr.toIntOrNull() ?: return
        val selectedMonth = selectedMonthStr.toIntOrNull() ?: return

        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                loader.loadDataForYear(selectedYear)
            }

            val favorableDays = mutableSetOf<CalendarDay>()
            val unfavorableDays = mutableSetOf<CalendarDay>()

            // Початок місяця
            val firstDayOfMonth = LocalDate.of(selectedYear, selectedMonth, 1)
            val daysInMonth = firstDayOfMonth.lengthOfMonth()  // правильно враховує високосність!

            for (dayOfMonth in 1..daysInMonth) {
                val date = firstDayOfMonth.withDayOfMonth(dayOfMonth)
                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                val moonInfo = withContext(Dispatchers.IO) {
                    db.moonDao().getByDate(dateStr)
                }

                // CalendarDay.from приймає рік, місяць (1-12), день (1-31)
                val calendarDay = CalendarDay.from(selectedYear, selectedMonth, dayOfMonth)

                when (moonInfo?.status) {
                    1 -> favorableDays.add(calendarDay)
                    -1 -> unfavorableDays.add(calendarDay)
                }
            }

            calendarView.removeDecorators()

            if (favorableDays.isNotEmpty()) {
                calendarView.addDecorator(FavorableDayDecorator(favorableDays.toList()))
            }
            if (unfavorableDays.isNotEmpty()) {
                calendarView.addDecorator(UnfavorableDayDecorator(unfavorableDays.toList()))
            }

            // Встановлюємо поточну дату календаря
            calendarView.setCurrentDate(CalendarDay.from(firstDayOfMonth.year, firstDayOfMonth.monthValue, 1))
        }
        }
//        val selectedYearStr = yearSpinner.selectedItem as? String ?: return
//        val selectedMonthStr = monthSpinner.selectedItem as? String ?: return
//
//        val selectedYear = selectedYearStr.toIntOrNull() ?: return
//        val selectedMonth = selectedMonthStr.toIntOrNull() ?: return
//
//        CoroutineScope(Dispatchers.Main).launch {
//            withContext(Dispatchers.IO) {
//                loader.loadDataForYear(selectedYear)
//            }
//
//            val favorableDays = mutableSetOf<CalendarDay>()
//            val unfavorableDays = mutableSetOf<CalendarDay>()
//
//            val calendar = Calendar.getInstance().apply {
//                set(selectedYear, selectedMonth - 1, 1)
//            }
//            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
//
//            for (day in 1..daysInMonth) {
//                val date = LocalDate.of(selectedYear, selectedMonth, day)
//                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
//
//                val moonInfo = withContext(Dispatchers.IO) {
//                    db.moonDao().getByDate(dateStr)
//                }
//
//                val calendarDay = CalendarDay.from(selectedYear, selectedMonth - 1, day)
//
//                when (moonInfo?.status) {
//                    1 -> favorableDays.add(calendarDay)
//                    -1 -> unfavorableDays.add(calendarDay)
//                }
//            }
//
//            calendarView.removeDecorators()
//
//            if (favorableDays.isNotEmpty()) {
//                calendarView.addDecorator(FavorableDayDecorator(favorableDays.toList()))
//            }
//            if (unfavorableDays.isNotEmpty()) {
//                calendarView.addDecorator(UnfavorableDayDecorator(unfavorableDays.toList()))
//            }
//
//            calendarView.setCurrentDate(CalendarDay.from(selectedYear, selectedMonth - 1, 1))
//        }


    private fun showDayInfo(date: LocalDate) {
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        Log.d("MainActivity", "Date selected: $dateStr")

        CoroutineScope(Dispatchers.Main).launch {
            var info = withContext(Dispatchers.IO) {
                db.moonDao().getByDate(dateStr)
            }

            if (info == null) {
                Log.d("MainActivity", "No data found, loading for $dateStr")
                withContext(Dispatchers.IO) {
                    loader.loadDataForDate(date)
                }
                info = withContext(Dispatchers.IO) {
                    db.moonDao().getByDate(dateStr)
                }
            }

//            if (info != null) {
//                val statusText = when (info.status) {
//                    1 -> getString(R.string.favorable_day)
//                    -1 -> getString(R.string.unfavorable_day)
//                    else -> getString(R.string.neutral_day)
//                }
//
//                infoText.text = buildString {
//    append("${getString(R.string.status)}: $statusText\n")
//    append("${getString(R.string.phase)}: ${info.phase_name}\n")          // phase_name
//    append("${getString(R.string.waxing)}: ${if (info.is_waxing) getString(R.string.yes) else getString(R.string.no)}\n")  // is_waxing
//    append("${getString(R.string.lunar_day)}: ${info.lunar_day}\n")      // lunar_day
//    append("${getString(R.string.zodiac_sign)}: ${info.zodiac_sign}")    // zodiac_sign
//}
//                Log.d("MainActivity", "Displayed data for $dateStr")
//            }
            if (info != null) {
                val evaluation = evaluateDay(
                    phaseName = info.phase_name,
                    isWaxing = info.is_waxing,
                    lunarDay = info.lunar_day,
                    zodiacSign = info.zodiac_sign,
                    weekday = LocalDate.parse(info.date).dayOfWeek.value  // день тижня з дати
                )

                val overallText = when (evaluation.overallStatus) {
                    1 -> "Сприятливий день"
                    -1 -> "Несприятливий день"
                    else -> "Нейтральний день"
                }

                infoText.text = buildString {
                    append("Дата: ${info.date}\n\n")
                    append("Загальний статус: $overallText (бал: ${evaluation.totalScore}/40)\n\n")
                    append("Оцінка факторів:\n")
                    append("• Фаза Місяця:     ${evaluation.phase.second} (${evaluation.phase.first})\n")
                    append("• Місячний день:   ${evaluation.lunarDay.second} (${evaluation.lunarDay.first})\n")
                    append("• Знак Зодіаку:    ${evaluation.zodiac.second} (${evaluation.zodiac.first})\n")
                    append("• День тижня:      ${evaluation.weekday.second} (${evaluation.weekday.first})\n")
                }
            } else {
                infoText.text = getString(R.string.data_not_loaded, date.toString())
            }
//            else {
//                infoText.text = getString(R.string.data_not_loaded, dateStr)
//                Log.w("MainActivity", "No data available for $dateStr")
//            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun evaluateDay(
        phaseName: String,
        isWaxing: Boolean,
        lunarDay: Int,
        zodiacSign: String,
        weekday: Int
    ): DayEvaluation {
        // Фаза Місяця (max 10 балів)
        val phaseScore = when {
            phaseName.contains("New", ignoreCase = true) || phaseName.contains("Full", ignoreCase = true) -> Pair(-4, "Нова/Повня — дуже погано")
            isWaxing -> Pair(8, "Зростаючий Місяць — добре для росту")
            else -> Pair(4, "Спадний Місяць — нейтрально/для зміцнення")
        }

        // Місячний день (max 10 балів)
        val lunarDayScore = when {
            lunarDay in 5..6 || lunarDay in 8..14 || lunarDay in 19..22 || lunarDay in 27..28 -> Pair(9, "Сприятливий місячний день")
            lunarDay in setOf(9, 10, 15, 23, 29) -> Pair(-8, "Сатанинський/важкий день")
            else -> Pair(2, "Нейтральний або середній день")
        }

        // Знак Зодіаку (max 10 балів)
        val zodiacScore = when (zodiacSign) {
            "Leo", "Virgo", "Taurus", "Capricorn", "Libra" -> Pair(10, "Дуже сприятливий знак")
            "Cancer", "Pisces", "Scorpio", "Aries", "Aquarius" -> Pair(-8, "Несприятливий знак")
            else -> Pair(3, "Нейтральний знак")
        }

        // День тижня (max 10 балів)
        val weekdayScore = when (weekday) {
            1 -> Pair(6, "Понеділок — добре для волосся")
            4 -> Pair(8, "Четвер — дуже сприятливий")
            6 -> Pair(7, "Субота — добре для зміцнення")
            7 -> Pair(-10, "Неділя — категорично погано")
            else -> Pair(4, "Середній день тижня")
        }

        return DayEvaluation(phaseScore, lunarDayScore, zodiacScore, weekdayScore)
    }
}

// Декоратори (залишилися без змін, але переконалися, що вони в тому ж файлі або імпортовані)
class FavorableDayDecorator(private val dates: List<CalendarDay>) : DayViewDecorator {
    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

    override fun decorate(view: DayViewFacade) {
        view.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#4CAF50")))
    }
}

class UnfavorableDayDecorator(private val dates: List<CalendarDay>) : DayViewDecorator {
    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

    override fun decorate(view: DayViewFacade) {
        view.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#F44336")))
    }
}

data class DayEvaluation(
    val phase: Pair<Int, String>,       // бал + пояснення
    val lunarDay: Pair<Int, String>,
    val zodiac: Pair<Int, String>,
    val weekday: Pair<Int, String>
) {
    val totalScore: Int
        get() = phase.first + lunarDay.first + zodiac.first + weekday.first

    val overallStatus: Int
        get() = when {
            totalScore >= 30 -> 1      // зелений
            totalScore >= 15 -> 0      // жовтий
            else -> -1                 // червоний
        }
}