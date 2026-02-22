// MoonApiService.kt
package com.kib.trimmoon

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Call

interface MoonApiService {
    @GET("api/rstt/oneday")
    fun getMoonData(
        @Query("date") date: String, // Format: YYYY-MM-DD
        @Query("coords") coords: String = "50.4501,30.5234", // Київ, Україна
        @Query("tz") tz: Int = 2 // UTC+2 для України
    ): Call<MoonData>
}
