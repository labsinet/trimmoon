package com.kib.trimmoon

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kib.trimmoon.MoonDao
import com.kib.trimmoon.MoonInfo

@Database(
    entities = [MoonInfo::class],
    version = 1,
    exportSchema = false
)
// @TypeConverters(Converters::class)  // Додайте, якщо створите Converters.kt для Double/Boolean тощо (хоча Room їх підтримує)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moonDao(): MoonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trimmoon-db"  // Змініть на "trimmoon-db" для консистентності
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}