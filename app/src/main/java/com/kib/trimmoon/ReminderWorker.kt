package com.example.haircutapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext) // додайте companion object з getDatabase
        val today = LocalDate.now()
        val dateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val info = db.moonDao().getByDate(dateStr)

        if (info != null && info.status == 1) { // positive
            NotificationHelper.showFavorableDayNotification(applicationContext, dateStr)
        }

        // Повторюємо щодня
        Result.success()
    }
}