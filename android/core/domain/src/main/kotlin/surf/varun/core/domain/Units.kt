package surf.varun.core.domain

import surf.varun.core.model.TemperatureUnit
import surf.varun.core.model.WindUnit
import kotlin.math.roundToInt

/** Unit conversion. Everything is stored and compared in knots / °C; this is display only. */
object Units {
    private const val MS_PER_KNOT = 0.514444
    private const val KMH_PER_KNOT = 1.852

    // Upper bounds (knots) of Beaufort 0..11; 12 is everything above
    private val BEAUFORT_LIMITS = doubleArrayOf(1.0, 4.0, 7.0, 11.0, 17.0, 22.0, 28.0, 34.0, 41.0, 48.0, 56.0, 64.0)

    fun beaufort(knots: Double): Int {
        val index = BEAUFORT_LIMITS.indexOfFirst { knots < it }
        return if (index < 0) 12 else index
    }

    fun wind(knots: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.KNOTS -> knots
        WindUnit.METERS_PER_SECOND -> knots * MS_PER_KNOT
        WindUnit.KILOMETERS_PER_HOUR -> knots * KMH_PER_KNOT
        WindUnit.BEAUFORT -> beaufort(knots).toDouble()
    }

    fun toKnots(value: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.KNOTS -> value
        WindUnit.METERS_PER_SECOND -> value / MS_PER_KNOT
        WindUnit.KILOMETERS_PER_HOUR -> value / KMH_PER_KNOT
        WindUnit.BEAUFORT -> BEAUFORT_LIMITS.getOrElse(value.toInt() - 1) { 0.0 }
    }

    /** Wind as the integer the UI prints (m/s keeps one decimal below 10). */
    fun formatWind(knots: Double, unit: WindUnit): String {
        val value = wind(knots, unit)
        return if (unit == WindUnit.METERS_PER_SECOND && value < 10) {
            ((value * 10).roundToInt() / 10.0).toString()
        } else {
            value.roundToInt().toString()
        }
    }

    fun temperature(celsius: Double, unit: TemperatureUnit): Double = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9 / 5 + 32
    }

    fun formatTemperature(celsius: Double, unit: TemperatureUnit): String =
        temperature(celsius, unit).roundToInt().toString()
}
