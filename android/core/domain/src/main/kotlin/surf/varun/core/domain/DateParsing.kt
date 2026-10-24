package surf.varun.core.domain

import surf.varun.core.model.DayLabel
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parsers for the zone-less date strings the backend sends.
 *
 * Forecast hours ("Sat 24 Oct 2026 14:00") are spot-local wall-clock time, the
 * Windguru convention. Station readings come in whatever format the station uses;
 * the three the web understands (`date.js#isConditionsOutdated`) are supported.
 */
object DateParsing {
    private val FORECAST = DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH)
    private val FORECAST_SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm", Locale.ENGLISH)

    private val ISO_LIKE = Regex("""^(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})(?::(\d{2}))?$""")
    private val DOTTED = Regex("""^(\d{2})\.(\d{2})\.(\d{4})\s+(\d{2}):(\d{2})$""")
    private val SLASHED = Regex("""^(\d{2})/(\d{2})/(\d{2})\s+(\d{2}):(\d{2}):(\d{2})$""")

    fun parseForecastTime(value: String?): LocalDateTime? {
        val text = value?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (text.split(' ').size < 5) return null
        return try {
            LocalDateTime.parse(text, FORECAST)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(text, FORECAST_SHORT_DAY)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun parseStationTime(value: String?): LocalDateTime? {
        val text = value?.trim() ?: return null
        return runCatching {
            ISO_LIKE.matchEntire(text)?.groupValues?.let { g ->
                LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            } ?: DOTTED.matchEntire(text)?.groupValues?.let { g ->
                LocalDateTime.of(g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt())
            } ?: SLASHED.matchEntire(text)?.groupValues?.let { g ->
                LocalDateTime.of(2000 + g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
            }
        }.getOrNull()
    }

    private val WEEKDAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    /** Maps the server's English daily labels to a [DayLabel] the UI can localize. */
    fun parseDayLabel(value: String?): DayLabel {
        val text = value?.trim().orEmpty()
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower == "today" -> DayLabel.Today
            lower == "tomorrow" -> DayLabel.Tomorrow
            lower.startsWith("day ") -> lower.removePrefix("day ").trim().toIntOrNull()
                ?.let { DayLabel.Offset(it - 1) } ?: DayLabel.Other(text)
            lower.take(3) in WEEKDAYS && lower.length <= 9 -> DayLabel.Weekday(WEEKDAYS.getValue(lower.take(3)))
            else -> DayLabel.Other(text)
        }
    }
}
