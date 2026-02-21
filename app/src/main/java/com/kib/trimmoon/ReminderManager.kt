package com.kib.trimmoon

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.Duration
import java.util.concurrent.TimeUnit

object ReminderManager {

    private const val UNIQUE_WORK_NAME = "daily_reminder"

    fun scheduleDailyReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateDelayToMorning(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun calculateDelayToMorning(): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(7).withMinute(30).withSecond(0).withNano(0)
        if (now.isAfter(target)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }
}