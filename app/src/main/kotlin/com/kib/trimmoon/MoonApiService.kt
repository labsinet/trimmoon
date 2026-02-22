package com.kib.trimmoon

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Call

interface MoonApiService {
    @GET("moon/phase")
    fun getMoonPhase(
        @Query("date") date: String,  // YYYY-MM-DD
        @Query("tz") tz: String = "2"  // Europe/Kiev, +2
    ): Call<MoonPhaseResponse>

    @GET("moon/position")
    fun getMoonPosition(
        @Query("date") date: String,  // YYYY-MM-DD
        @Query("coords") coords: String = "50.45,30.52",  // Київ lat,lon
        @Query("tz") tz: String = "2"
    ): Call<MoonPositionResponse>
}