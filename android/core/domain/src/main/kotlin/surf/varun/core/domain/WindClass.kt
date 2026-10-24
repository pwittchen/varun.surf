package surf.varun.core.domain

/**
 * Wind classes in knots, matching `getWindClass` / `getMapWindClass` in the web's
 * `weather.js`. Cards and forecast cells use [of] (everything under 12 kts is
 * [WEAK]); the map uses [forMap], which splits that range into [CALM] and [LIGHT]
 * so it reads as a cold-to-warm scale.
 */
enum class WindClass {
    CALM, LIGHT, WEAK, MODERATE, STRONG, EXTREME;

    val isRideable: Boolean get() = this >= MODERATE

    companion object {
        fun of(knots: Double): WindClass = when {
            knots < 12 -> WEAK
            knots < 18 -> MODERATE
            knots <= 25 -> STRONG
            else -> EXTREME
        }

        fun forMap(knots: Double): WindClass = when {
            knots < 5 -> CALM
            knots < 12 -> LIGHT
            else -> of(knots)
        }

        /** Class of the average of wind and gusts, which the web tints whole cards and rows by. */
        fun ofAverage(wind: Double, gusts: Double): WindClass = of((wind + gusts) / 2)
    }
}
