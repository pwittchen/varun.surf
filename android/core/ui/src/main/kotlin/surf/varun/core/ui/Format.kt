package surf.varun.core.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import surf.varun.core.domain.Countries
import surf.varun.core.domain.Units
import surf.varun.core.model.DayLabel
import surf.varun.core.model.SpotInfo
import surf.varun.core.model.Spot
import surf.varun.core.model.AiAnalysis
import surf.varun.core.model.TemperatureUnit
import surf.varun.core.model.WindUnit
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Formatting shared by every screen: units, dates, ages, localized server labels. */
object Format {
    fun windUnitLabel(context: Context, unit: WindUnit): String = context.getString(
        when (unit) {
            WindUnit.KNOTS -> R.string.unit_knots
            WindUnit.METERS_PER_SECOND -> R.string.unit_ms
            WindUnit.KILOMETERS_PER_HOUR -> R.string.unit_kmh
            WindUnit.BEAUFORT -> R.string.unit_beaufort
        },
    )

    fun wind(knots: Double, unit: WindUnit): String = Units.formatWind(knots, unit)

    fun windWithUnit(context: Context, knots: Double, unit: WindUnit): String =
        "${wind(knots, unit)} ${windUnitLabel(context, unit)}"

    fun temperature(context: Context, celsius: Double, unit: TemperatureUnit): String =
        Units.formatTemperature(celsius, unit) + context.getString(
            if (unit == TemperatureUnit.CELSIUS) R.string.unit_celsius else R.string.unit_fahrenheit,
        )

    fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    /**
     * "Today", "Tomorrow", then weekdays. The server sends "Day 3" onwards; with the
     * spot's [today] known those become the weekday they fall on.
     */
    fun dayLabel(context: Context, label: DayLabel?, fallback: String, today: LocalDate? = null): String = when (label) {
        DayLabel.Today -> context.getString(R.string.day_today)
        DayLabel.Tomorrow -> context.getString(R.string.day_tomorrow)
        is DayLabel.Offset -> today?.let { weekday(context, it.plusDays(label.days.toLong()).dayOfWeek) }
            ?: context.getString(R.string.day_n, label.days + 1)
        is DayLabel.Weekday -> weekday(context, label.dayOfWeek)
        is DayLabel.Other -> label.text
        null -> fallback
    }

    fun weekday(context: Context, day: DayOfWeek, style: TextStyle = TextStyle.SHORT): String =
        day.getDisplayName(style, locale(context)).replaceFirstChar { it.titlecase(locale(context)) }

    /** "Sat 14:00" - the map timeline and forecast columns. */
    fun dayAndHour(context: Context, time: LocalDateTime): String =
        "${weekday(context, time.dayOfWeek)} ${hour(context, time)}"

    fun hour(context: Context, time: LocalDateTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h a"
        return time.format(formatter(pattern, locale(context)))
    }

    /** Like [hour], but keeps the minutes in 12-hour format too - live readings rarely land on the hour. */
    fun clock(context: Context, time: LocalDateTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(formatter(pattern, locale(context)))
    }

    /** Formatters are immutable and parsing a pattern is not free: the timeline slider asks on every step. */
    private val formatters = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    private fun formatter(pattern: String, locale: Locale): DateTimeFormatter =
        formatters.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }

    /** "Sat 24 Oct" for day headers. */
    fun dayHeader(context: Context, date: LocalDate): String =
        "${weekday(context, date.dayOfWeek)} ${date.dayOfMonth} " +
            date.month.getDisplayName(TextStyle.SHORT, locale(context))

    /** An instant in the device zone, e.g. the AI analysis generation time. */
    fun instant(context: Context, instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale(context))
            .format(instant.atZone(zone))

    fun time(context: Context, instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        hour(context, instant.atZone(zone).toLocalDateTime())

    /** "just now", "3 min ago", "2 h ago", "3 days ago". */
    fun age(context: Context, age: Duration): String {
        val res = context.resources
        return when {
            age.toMinutes() < 1 -> context.getString(R.string.just_now)
            age.toHours() < 1 -> res.getQuantityString(R.plurals.minutes_ago, age.toMinutes().toInt(), age.toMinutes().toInt())
            age.toDays() < 1 -> res.getQuantityString(R.plurals.hours_ago, age.toHours().toInt(), age.toHours().toInt())
            else -> res.getQuantityString(R.plurals.days_ago, age.toDays().toInt(), age.toDays().toInt())
        }
    }

    fun countryName(context: Context, country: String): String =
        Countries.of(country).localizedName(context.isPolish())

    fun distanceKm(context: Context, km: Double): String =
        context.getString(R.string.km_away, if (km < 10) "%.1f".format(locale(context), km) else km.toInt().toString())
}

@Composable
@ReadOnlyComposable
fun windUnitLabel(): String {
    LocalConfiguration.current
    return Format.windUnitLabel(LocalContext.current, LocalUserSettings.current.windUnit)
}

@Composable
@ReadOnlyComposable
fun formatWind(knots: Double): String = Format.wind(knots, LocalUserSettings.current.windUnit)

@Composable
@ReadOnlyComposable
fun formatTemperature(celsius: Double): String {
    LocalConfiguration.current
    return Format.temperature(LocalContext.current, celsius, LocalUserSettings.current.temperatureUnit)
}

@Composable
@ReadOnlyComposable
fun countryName(country: String): String {
    LocalConfiguration.current
    return Format.countryName(LocalContext.current, country)
}

/** `spotInfoPL` in Polish, `spotInfo` otherwise, falling back to whichever exists. */
@Composable
@ReadOnlyComposable
fun Spot.localizedInfo(): SpotInfo? = if (isPolish()) infoPl ?: info else info ?: infoPl

/**
 * Spec 4.5: the analysis in the app language; the other one, flagged, when only
 * that exists. Second value is true when the text is in the other language.
 */
@Composable
@ReadOnlyComposable
fun Spot.localizedAnalysis(): Pair<AiAnalysis, Boolean>? {
    val polish = isPolish()
    val preferred = if (polish) aiAnalysisPl else aiAnalysisEn
    val other = if (polish) aiAnalysisEn else aiAnalysisPl
    return preferred?.let { it to false } ?: other?.let { it to true }
}

@Composable
fun updatedAgoText(lastSuccess: Instant?, now: Instant): String {
    val context = LocalContext.current
    return if (lastSuccess == null) {
        stringResource(R.string.never_updated)
    } else {
        stringResource(R.string.updated_ago, Format.age(context, Duration.between(lastSuccess, now)))
    }
}
