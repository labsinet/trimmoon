package com.kib.trimmoon

import MoonDao
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MoonInfo::class],
    version = 1,
    exportSchema = true  // Змініть на true для генерації схеми (дивіться в schemas папці після білду)
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