package surf.varun.core.domain

import surf.varun.core.model.Coordinates
import java.time.ZoneId

/**
 * The time zone a spot's forecast and station times are written in.
 *
 * The backend sends spot-local wall-clock times without a zone (open question Q1
 * in the spec). Instead of shipping a tz-boundary dataset, the zone comes from the
 * spot's country, split by coordinates for the few countries the spot list
 * crosses several zones in. An unknown country falls back to the device zone, which
 * is what the web does for every spot.
 */
object SpotTimeZones {
    private val BY_COUNTRY: Map<String, String> = mapOf(
        "Poland" to "Europe/Warsaw", "Czech Republic" to "Europe/Prague", "Austria" to "Europe/Vienna",
        "Belgium" to "Europe/Brussels", "Switzerland" to "Europe/Zurich", "Latvia" to "Europe/Riga",
        "Lithuania" to "Europe/Vilnius", "Estonia" to "Europe/Tallinn", "Denmark" to "Europe/Copenhagen",
        "Sweden" to "Europe/Stockholm", "Norway" to "Europe/Oslo", "Iceland" to "Atlantic/Reykjavik",
        "Italy" to "Europe/Rome", "Malta" to "Europe/Malta", "Greece" to "Europe/Athens",
        "France" to "Europe/Paris", "Germany" to "Europe/Berlin", "Netherlands" to "Europe/Amsterdam",
        "Croatia" to "Europe/Zagreb", "Slovenia" to "Europe/Ljubljana", "Serbia" to "Europe/Belgrade",
        "Montenegro" to "Europe/Podgorica", "Albania" to "Europe/Tirane", "Macedonia" to "Europe/Skopje",
        "Bulgaria" to "Europe/Sofia", "Romania" to "Europe/Bucharest", "Ireland" to "Europe/Dublin",
        "United Kingdom" to "Europe/London", "UK" to "Europe/London", "Turkey" to "Europe/Istanbul",
        "Morocco" to "Africa/Casablanca", "Egypt" to "Africa/Cairo", "Cape Verde" to "Atlantic/Cape_Verde",
        "Mauritius" to "Indian/Mauritius", "Peru" to "America/Lima", "Chile" to "America/Santiago",
        "Namibia" to "Africa/Windhoek", "South Africa" to "Africa/Johannesburg",
        "Tanzania" to "Africa/Dar_es_Salaam", "Costa Rica" to "America/Costa_Rica",
        "Turks and Caicos" to "America/Grand_Turk", "Sri Lanka" to "Asia/Colombo",
        "Vietnam" to "Asia/Ho_Chi_Minh", "Philippines" to "Asia/Manila", "Thailand" to "Asia/Bangkok",
        "Indonesia" to "Asia/Makassar", "Taiwan" to "Asia/Taipei", "India" to "Asia/Kolkata",
        "Oman" to "Asia/Muscat", "United Arab Emirates" to "Asia/Dubai", "Canada" to "America/Vancouver",
        "Dominican Republic" to "America/Santo_Domingo", "Aruba" to "America/Aruba",
        "Colombia" to "America/Bogota", "Venezuela" to "America/Caracas", "Ecuador" to "America/Guayaquil",
        "Argentina" to "America/Argentina/Buenos_Aires",
    )

    fun zoneFor(country: String?, coordinates: Coordinates?, fallback: ZoneId = ZoneId.systemDefault()): ZoneId {
        val id = idFor(country, coordinates) ?: return fallback
        return runCatching { ZoneId.of(id) }.getOrDefault(fallback)
    }

    internal fun idFor(country: String?, coordinates: Coordinates?): String? {
        val lat = coordinates?.lat
        val lon = coordinates?.lon
        return when (country) {
            "Spain" -> if (lon != null && lon < -12) "Atlantic/Canary" else "Europe/Madrid"
            "Portugal" -> when {
                lon != null && lon < -20 -> "Atlantic/Azores"
                lon != null && lon < -14 -> "Atlantic/Madeira"
                else -> "Europe/Lisbon"
            }
            "USA" -> when {
                lon == null -> "America/New_York"
                lon < -150 -> "Pacific/Honolulu"
                lon < -114 -> "America/Los_Angeles"
                lon < -102 -> "America/Denver"
                lon < -87 -> "America/Chicago"
                else -> "America/New_York"
            }
            "Brazil" -> if (lat != null && lat < -20) "America/Sao_Paulo" else "America/Fortaleza"
            "Australia" -> when {
                lon == null -> "Australia/Sydney"
                lon < 129 -> "Australia/Perth"
                lat != null && lat > -28.2 -> "Australia/Brisbane"
                lat != null && lat < -36.5 && lon < 150 -> "Australia/Melbourne"
                else -> "Australia/Sydney"
            }
            "Mexico" -> if (lon != null && lon < -105) "America/Mazatlan" else "America/Cancun"
            else -> BY_COUNTRY[country]
        }
    }
}
