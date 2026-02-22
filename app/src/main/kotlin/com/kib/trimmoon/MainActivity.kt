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
import android.widget.TextView
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
            // Можна показати пояснення або Toast, що нагадування не працюватимуть без дозволу
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        calendarView = findViewById(R.id.calendarView)
        infoText = findViewById(R.id.infoText)
        yearSpinner = findViewById(R.id.yearSpinner)
        monthSpinner = findViewById(R.id.monthSpinner)
        switchReminders = findViewById(R.id.switchReminders)

        db = AppDatabase.getDatabase(this)
        loader = DataLoader(db.moonDao())

        // Налаштування спінерів
        setupSpinners()

        // Завантаження початкового стану нагадувань
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

        // Якщо нагадування вже увімкнено — плануємо
        if (remindersEnabled) {
            ReminderManager.scheduleDailyReminder(this)
        }

        // Ініціалізація каналу сповіщень
        NotificationHelper.createNotificationChannel(this)

        // Налаштування календаря
        setupCalendar()

        // Початкове оновлення календаря
        updateCalendar()

        // Обробник вибору дати
        calendarView.setOnDateChangedListener { _, date, _ ->
            val selectedDate = LocalDate.of(date.year, date.month + 1, date.day)
            showDayInfo(selectedDate)
        }
    }

    private fun setupSpinners() {
        TODO("Not yet implemented")
    }

    private fun updateCalendar() {
        val selectedYear = yearSpinner.selectedItem as? Int ?: return
        val selectedMonth = (monthSpinner.selectedItem as? String)?.toIntOrNull() ?: return

        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                loader.loadDataForYear(selectedYear)
            }

            // Створюємо декоратори для розмальовування днів
            val favorableDays = mutableListOf<CalendarDay>()
            val unfavorableDays = mutableListOf<CalendarDay>()

            // Отримуємо всі дні місяця і перевіряємо їх статус
            val calendar = Calendar.getInstance()
            calendar.set(selectedYear, selectedMonth - 1, 1)
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            for (day in 1..daysInMonth) {
                val date = LocalDate.of(selectedYear, selectedMonth, day)
                val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                val moonInfo = withContext(Dispatchers.IO) {
                    db.moonDao().getByDate(dateStr)
                }

                val calendarDay = CalendarDay.from(selectedYear, selectedMonth - 1, day)
                when (moonInfo?.status) {
                    1 -> favorableDays.add(calendarDay)
                    -1 -> unfavorableDays.add(calendarDay)
                }
            }

            // Очищаємо попередні декоратори
            calendarView.removeDecorators()

            // Додаємо нові декоратори
            if (favorableDays.isNotEmpty()) {
                calendarView.addDecorator(FavorableDayDecorator(favorableDays))
            }
            if (unfavorableDays.isNotEmpty()) {
                calendarView.addDecorator(UnfavorableDayDecorator(unfavorableDays))
            }

            // Встановлюємо дату календаря
            calendarView.setCurrentDate(CalendarDay.from(selectedYear, selectedMonth - 1, 1))
        }
    }

    private fun setupCalendar() {
        // Налаштування календаря - встановлюємо поточну дату
        val calendar = Calendar.getInstance()
        calendarView.setCurrentDate(calendar)
    }

    private fun showDayInfo(date: LocalDate) {
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        println("MainActivity: Date selected: $dateStr")
        CoroutineScope(Dispatchers.Main).launch {
            var info = withContext(Dispatchers.IO) {
                val result = db.moonDao().getByDate(dateStr)
                println("MainActivity: Data from DB for $dateStr: $result")
                result
            }

            // If no data found, try to load it
            if (info == null) {
                println("MainActivity: No data found, loading data for $dateStr")
                withContext(Dispatchers.IO) {
                    loader.loadDataForDate(date)
                }
                // Query again after loading
                info = withContext(Dispatchers.IO) {
                    val result = db.moonDao().getByDate(dateStr)
                    println("MainActivity: Data after loading for $dateStr: $result")
                    result
                }
            }

            if (info != null) {
                val statusText = when (info.status) {
                    1 -> getString(R.string.favorable_day)
                    -1 -> getString(R.string.unfavorable_day)
                    else -> getString(R.string.neutral_day)
                }
                infoText.text = buildString {
                    append("${getString(R.string.status)}: $statusText\n")
                    append("${getString(R.string.phase)}: ${info.phase_name}\n")
                    append("${getString(R.string.waxing)}: ${if (info.is_waxing) getString(R.string.yes) else getString(R.string.no)}\n")
                    append("${getString(R.string.lunar_day)}: ${info.lunar_day}\n")
                    append("${getString(R.string.zodiac_sign)}: ${info.zodiac_sign}")
                }
                println("MainActivity: Displayed data for $dateStr")
            } else {
                infoText.text = getString(R.string.data_not_loaded, dateStr)
                println("MainActivity: No data available for $dateStr")
            }
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
}

// Декоратор для позитивних днів (зелений колір)
class FavorableDayDecorator(private val dates: List<CalendarDay>) : DayViewDecorator {
    override fun shouldDecorate(day: CalendarDay): Boolean {
        return dates.contains(day)
    }

    override fun decorate(view: DayViewFacade) {
        view.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#4CAF50")))
        view.setDaysDisabled(false)
    }
}

// Декоратор для негативних днів (червоний колір)
class UnfavorableDayDecorator(private val dates: List<CalendarDay>) : DayViewDecorator {
    override fun shouldDecorate(day: CalendarDay): Boolean {
        return dates.contains(day)
    }

    override fun decorate(view: DayViewFacade) {
        view.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#F44336")))
        view.setDaysDisabled(false)
    }
}