import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MoonDao {
    @Insert
    suspend fun insert(moonInfo: MoonInfo)

    @Query("SELECT * FROM moon_info WHERE date = :date")
    suspend fun getByDate(date: String): MoonInfo?
}
