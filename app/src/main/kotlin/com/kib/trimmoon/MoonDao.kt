package com.kib.trimmoon

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MoonDao {
    @Insert
    fun insert(moonInfo: MoonInfo)

    @Query("SELECT * FROM moon_info WHERE date = :date")
    fun getByDate(date: String): MoonInfo?

    @Query("SELECT * FROM moon_info WHERE date LIKE :prefix")
    fun getByMonth(prefix: String): List<MoonInfo>
}
