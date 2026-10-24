package surf.varun.feature.spots

import surf.varun.core.domain.Countries
import surf.varun.core.domain.Geo
import surf.varun.core.model.Coordinates
import surf.varun.core.model.DayLabel
import surf.varun.core.model.Spot
import surf.varun.core.ui.SpotCardModel
import java.time.ZonedDateTime

enum class SpotSort { FIRING, NAME, FORECAST_TODAY, DISTANCE }

data class SpotFilters(
    val country: String? = null,
    val liveOnly: Boolean = false,
    val nearby: Boolean = false,
    val windyToday: Boolean = false,
) {
    val isActive: Boolean get() = country != null || liveOnly || nearby || windyToday
}

data class CountryCount(val name: String, val count: Int)

/** Filtering and ordering of the spots list - pure, so it is tested without Android. */
object SpotListing {
    /** "Spots within 50 km" (spec 2.5.3). */
    const val NEARBY_KM = 50.0

    /** Today's daily forecast as the average of wind and gusts, which is what the web tints rows by. */
    fun todayAverage(spot: Spot): Double? =
        spot.daily.firstOrNull { it.day == DayLabel.Today }?.let { (it.wind + it.gusts) / 2 }

    fun distanceKm(spot: Spot, location: Coordinates?): Double? {
        val here = location ?: return null
        val there = spot.coordinates ?: return null
        return Geo.distanceKm(here, there)
    }

    fun apply(
        spots: List<Spot>,
        filters: SpotFilters,
        sort: SpotSort,
        favourites: Set<Int>,
        location: Coordinates?,
        now: ZonedDateTime,
    ): List<SpotCardModel> {
        val models = spots.asSequence()
            .filter { filters.country == null || it.country == filters.country }
            .filter { !filters.liveOnly || it.live != null }
            .filter { !filters.windyToday || (todayAverage(it) ?: 0.0) >= WINDY_KNOTS }
            .map { spot -> SpotCardModel.of(spot, spot.wgId in favourites, now, distanceKm(spot, location)) }
            .filter { !filters.nearby || location == null || (it.distanceKm ?: Double.MAX_VALUE) <= NEARBY_KM }
            .toList()
        return when (sort) {
            SpotSort.FIRING -> models.sortedByDescending { it.reading?.average ?: Double.NEGATIVE_INFINITY }
            SpotSort.NAME -> models.sortedBy { it.spot.name.lowercase() }
            SpotSort.FORECAST_TODAY -> models.sortedByDescending { todayAverage(it.spot) ?: Double.NEGATIVE_INFINITY }
            SpotSort.DISTANCE -> models.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        }
    }

    fun countries(spots: List<Spot>): List<CountryCount> =
        spots.groupingBy { it.country }.eachCount()
            .filterKeys { it.isNotBlank() }
            .map { (name, count) -> CountryCount(name, count) }
            .sortedBy { it.name }

    /** Country named by a route: the server's name or the web's URL slug ("czech-republic"). */
    fun resolveCountry(value: String?, known: Collection<String>): String? {
        if (value.isNullOrBlank()) return null
        return known.firstOrNull { it.equals(value, ignoreCase = true) } ?: Countries.fromSlug(value, known)
    }

    private const val WINDY_KNOTS = 12.0

}
