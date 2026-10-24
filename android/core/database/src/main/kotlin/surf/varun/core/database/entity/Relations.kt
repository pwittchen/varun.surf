package surf.varun.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/** What the spots list needs per spot: static data, live reading, daily forecast. */
data class SpotWithConditions(
    @Embedded val spot: SpotEntity,
    @Relation(parentColumn = "wgId", entityColumn = "wgId")
    val live: LiveConditionsEntity?,
    @Relation(parentColumn = "wgId", entityColumn = "wgId")
    val daily: List<DailyForecastEntity>,
)
