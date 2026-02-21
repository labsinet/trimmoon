package com.kib.trimmoon  // must match your namespace/applicationId

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MoonInfo::class],  // ← list ALL your @Entity classes here!
    version = 1,
    exportSchema = false           // set true later if you want schema checks
)
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
                    "haircut-db"  // or "trimmoon-db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}