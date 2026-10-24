package surf.varun.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Times are stored as primitives: spot-local wall-clock times as ISO strings
 * ("2026-10-24T14:00"), instants as epoch milliseconds. Mapping to java.time
 * happens in core:data.
 */

@Entity(tableName = "spot")
data class SpotEntity(
    @PrimaryKey val wgId: Int,
    val name: String,
    val country: String,
    /** Position in the server's list, so the default order survives restarts. */
    val serverOrder: Int,
    val windguruUrl: String?,
    val windfinderUrl: String?,
    val icmUrl: String?,
    val webcamUrl: String?,
    val locationUrl: String?,
    val lat: Double?,
    val lon: Double?,
    val photoUrl: String?,
    @Embedded(prefix = "info_") val info: SpotInfoColumns?,
    @Embedded(prefix = "info_pl_") val infoPl: SpotInfoColumns?,
    /** Sponsors as "id|main|name|link" lines - rare, small, never queried. */
    val sponsors: String?,
    val lastUpdated: String?,
    val fetchedAt: Long,
)

data class SpotInfoColumns(
    val type: String?,
    val bestWind: String?,
    val waterTemp: String?,
    val experience: String?,
    val launch: String?,
    val hazards: String?,
    val season: String?,
    val description: String?,
)

@Entity(tableName = "live_conditions")
data class LiveConditionsEntity(
    @PrimaryKey val wgId: Int,
    val rawDate: String,
    val observedAt: String?,
    val wind: Int,
    val gusts: Int,
    val direction: String?,
    val temp: Int?,
    val fetchedAt: Long,
)

@Entity(tableName = "live_history", primaryKeys = ["wgId", "position"])
data class LiveHistoryEntity(
    val wgId: Int,
    val position: Int,
    val rawDate: String,
    val observedAt: String?,
    val wind: Int,
    val gusts: Int,
    val direction: String?,
    val temp: Int?,
)

/** Shared columns of daily and hourly rows. */
data class ForecastColumns(
    val rawDate: String,
    val wind: Double,
    val gusts: Double,
    val direction: String?,
    val temp: Double?,
    val precipitation: Double?,
    val cloudCoverPercent: Double?,
    val pressureHpa: Double?,
    val wave: Double?,
    val wavePeriod: Double?,
    val waveDirection: String?,
)

@Entity(tableName = "daily_forecast", primaryKeys = ["wgId", "position"])
data class DailyForecastEntity(
    val wgId: Int,
    val position: Int,
    @Embedded val values: ForecastColumns,
)

@Entity(tableName = "hourly_forecast", primaryKeys = ["wgId", "model", "time"])
data class HourlyForecastEntity(
    val wgId: Int,
    val model: String,
    val time: String,
    @Embedded val values: ForecastColumns,
)

/** When a spot's detail (hourly forecast of one model, history) was last fetched. */
@Entity(tableName = "spot_detail", primaryKeys = ["wgId", "model"])
data class SpotDetailEntity(
    val wgId: Int,
    val model: String,
    val fetchedAt: Long,
)

@Entity(tableName = "ai_analysis", primaryKeys = ["wgId", "lang"])
data class AiAnalysisEntity(
    val wgId: Int,
    val lang: String,
    val text: String,
    val createdAt: Long?,
)

@Entity(tableName = "available_model", primaryKeys = ["wgId", "key"])
data class AvailableModelEntity(
    val wgId: Int,
    val key: String,
    val name: String,
    val position: Int,
)

@Entity(tableName = "favourite")
data class FavouriteEntity(
    @PrimaryKey val wgId: Int,
    val position: Int,
)

@Entity(tableName = "alert", indices = [Index("wgId")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wgId: Int,
    val spotName: String,
    val minWind: Int,
    val maxWind: Int?,
    /** Bitmask of Direction ordinals; 0 = any direction. */
    val directions: Int,
    val trigger: String,
    val horizonDays: Int,
    val minConsecutiveHours: Int,
    val quietStartHour: Int?,
    val quietEndHour: Int?,
    val lastFiredAt: Long?,
    val mutedUntil: Long?,
    val enabled: Boolean,
)

@Entity(tableName = "recent_spot")
data class RecentSpotEntity(
    @PrimaryKey val wgId: Int,
    @ColumnInfo(name = "openedAt") val openedAt: Long,
)

@Entity(tableName = "recent_search")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val at: Long,
)
