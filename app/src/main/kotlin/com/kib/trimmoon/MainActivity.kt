package com.kib.trimmoon

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CalendarView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Year
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var loader: DataLoader
    private lateinit var calendarView: CalendarView
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

        // Початкове оновлення календаря
        updateCalendar()

        // Обробник вибору дати
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            CoroutineScope(Dispatchers.Main).launch {
                var info = withContext(Dispatchers.IO) {
                    db.moonDao().getByDate(dateStr)
                }

                // If no data found, try to load it
                if (info == null) {
                    withContext(Dispatchers.IO) {
                        loader.loadDataForYear(year)
                    }
                    // Query again after loading
                    info = withContext(Dispatchers.IO) {
                        db.moonDao().getByDate(dateStr)
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
                } else {
                    infoText.text = getString(R.string.data_not_loaded, dateStr)
                }
            }
        }
    }

    private fun setupSpinners() {
        // Роки: наприклад від 2020 до 2035
        val years = (2020..2035).toList()
        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        yearSpinner.setSelection(years.indexOf(Year.now().value))

        // Місяці: 01–12
        val months = (1..12).map { it.toString().padStart(2, '0') }
        monthSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        monthSpinner.setSelection(LocalDate.now().monthValue - 1)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                updateCalendar()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        yearSpinner.onItemSelectedListener = listener
        monthSpinner.onItemSelectedListener = listener
    }

    private fun updateCalendar() {
        val selectedYear = yearSpinner.selectedItem as? Int ?: return
        val selectedMonth = (monthSpinner.selectedItem as? String)?.toIntOrNull() ?: return

        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                loader.loadDataForYear(selectedYear)
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