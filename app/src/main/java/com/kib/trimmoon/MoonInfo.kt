import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moon_info")
data class MoonInfo(
    @PrimaryKey val date: String,
    val phaseName: String,
    val isWaxing: Boolean,
    val illumination: Double,
    val lunarDay: Int,
    val zodiacSign: String,
    val status: Int // 1=positive, -1=negative, 0=neutral
)