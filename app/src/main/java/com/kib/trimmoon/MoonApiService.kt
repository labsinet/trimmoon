// MoonApiService.kt
import com.kib.trimmoon.MoonData
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Call

interface MoonApiService {
    @GET("basic") // Basic endpoint for moon data
    fun getMoonData(
        @Query("date") date: String, // Format: YYYY-MM-DD
        @Query("tz") tz: String = "Europe/Kiev" // Для України
    ): Call<MoonData>
}
