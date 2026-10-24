package surf.varun.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import surf.varun.core.database.dao.AlertDao
import surf.varun.core.database.dao.FavouriteDao
import surf.varun.core.database.dao.RecentDao
import surf.varun.core.database.dao.SpotDao
import surf.varun.core.database.entity.AiAnalysisEntity
import surf.varun.core.database.entity.AlertEntity
import surf.varun.core.database.entity.AvailableModelEntity
import surf.varun.core.database.entity.DailyForecastEntity
import surf.varun.core.database.entity.FavouriteEntity
import surf.varun.core.database.entity.HourlyForecastEntity
import surf.varun.core.database.entity.LiveConditionsEntity
import surf.varun.core.database.entity.LiveHistoryEntity
import surf.varun.core.database.entity.RecentSearchEntity
import surf.varun.core.database.entity.RecentSpotEntity
import surf.varun.core.database.entity.SpotDetailEntity
import surf.varun.core.database.entity.SpotEntity

@Database(
    entities = [
        SpotEntity::class,
        LiveConditionsEntity::class,
        LiveHistoryEntity::class,
        DailyForecastEntity::class,
        HourlyForecastEntity::class,
        SpotDetailEntity::class,
        AiAnalysisEntity::class,
        AvailableModelEntity::class,
        FavouriteEntity::class,
        AlertEntity::class,
        RecentSpotEntity::class,
        RecentSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VarunDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao

    abstract fun favouriteDao(): FavouriteDao

    abstract fun alertDao(): AlertDao

    abstract fun recentDao(): RecentDao
}
