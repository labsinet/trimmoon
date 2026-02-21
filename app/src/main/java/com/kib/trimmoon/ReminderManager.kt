package com.kib.trimmoon

import java.time.Duration
import java.time.LocalDateTime

// ReminderManager.kt
object ReminderManager {

    private const val UNIQUE_WORK_NAME = "daily_haircut_reminder"

    fun scheduleDailyReminder(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateInitialDelayToMorning(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun calculateInitialDelayToMorning(): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(7).withMinute(30).withSecond(0).withNano(0)
        if (now.isAfter(target)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target).toMillis()
    }
}