package surf.varun.core.domain

import surf.varun.core.model.SkillLevel
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * 1:1 port of the web's `calculator.js`. Rounding uses JavaScript's `Math.round`
 * (half up) rather than Kotlin's half-even, so both sides agree to the centimetre.
 */
object KiteCalculator {
    private val COMMON_KITE_SIZES = listOf(7, 9, 10, 12, 14, 15, 17)

    private val KITE_FACTOR = mapOf(
        SkillLevel.BEGINNER_FLAT to 3.0,
        SkillLevel.BEGINNER_SMALL to 2.8,
        SkillLevel.INTERMEDIATE_FLAT to 2.5,
        SkillLevel.INTERMEDIATE_MEDIUM to 2.3,
        SkillLevel.ADVANCED_FLAT to 2.2,
        SkillLevel.ADVANCED_MEDIUM to 2.0,
        SkillLevel.ADVANCED_LARGE to 1.8,
    )

    private val TWIN_TIP_ADJUSTMENT = mapOf(
        SkillLevel.BEGINNER_FLAT to 1.15,
        SkillLevel.BEGINNER_SMALL to 1.10,
        SkillLevel.INTERMEDIATE_FLAT to 1.0,
        SkillLevel.INTERMEDIATE_MEDIUM to 0.98,
        SkillLevel.ADVANCED_FLAT to 0.92,
        SkillLevel.ADVANCED_MEDIUM to 0.90,
    )

    enum class Warning {
        INVALID_WIND_SPEED,
        INVALID_WEIGHT,
        WEIGHT_TOO_LOW,
        SKILL_LEVEL_REQUIRED,
        WEIGHT_TOO_HIGH,
        EXTREME_WIND,
        LOW_WIND,
        INSUFFICIENT_WIND_WAVES,
    }

    sealed interface Board {
        val text: String

        data class TwinTip(val lengthCm: Int, val widthCm: Int) : Board {
            override val text: String get() = "$lengthCm x $widthCm cm (Twin Tip)"
        }

        data class Directional(val length: String) : Board {
            override val text: String get() = "$length (Surfboard/Directional)"
        }
    }

    data class Result(val kiteSizeM2: Int, val board: Board)

    fun kiteSize(windKnots: Double, weightKg: Double, skill: SkillLevel?): Int {
        val factor = KITE_FACTOR[skill] ?: 2.5
        val size = weightKg * factor / windKnots
        // reduce() keeps the earlier size on a tie, exactly like the JS reducer
        return COMMON_KITE_SIZES.reduce { prev, curr -> if (abs(curr - size) < abs(prev - size)) curr else prev }
    }

    fun boardSize(weightKg: Double, skill: SkillLevel): Board {
        val key = skill.key
        val directional = key.contains("large") || (key.contains("medium") && key.contains("advanced"))
        if (directional) {
            val length = when {
                weightKg < 70 -> "5'6\""
                weightKg < 85 -> "5'10\""
                else -> "6'0\""
            }
            return Board.Directional(length)
        }

        val effectiveWeight = min(weightKg, 115.0)
        val baseSize = 125 + (effectiveWeight - 50) * 0.5
        val adjustment = TWIN_TIP_ADJUSTMENT[skill] ?: 1.0
        var boardSize = jsRound(baseSize * adjustment)
        boardSize = jsRound(boardSize / 2.0) * 2

        val maxSize = if (key.contains("intermediate") || key.contains("advanced")) 142 else 160
        boardSize = boardSize.coerceIn(136, maxSize)

        val width = min(38 + floor((boardSize - 136) / 4.0).toInt(), 46)
        return Board.TwinTip(boardSize, width)
    }

    /** Same checks, in the same order, as `validateInput` on the web. */
    fun validate(windKnots: Double?, weightKg: Double?, skill: SkillLevel?): Warning? {
        if (windKnots == null || windKnots.isNaN() || windKnots == 0.0 || windKnots < 5 || windKnots > 50) {
            return Warning.INVALID_WIND_SPEED
        }
        if (weightKg == null || weightKg.isNaN() || weightKg == 0.0 || weightKg > 150) return Warning.INVALID_WEIGHT
        if (weightKg < 40) return Warning.WEIGHT_TOO_LOW
        if (skill == null) return Warning.SKILL_LEVEL_REQUIRED
        if (weightKg > 120) return Warning.WEIGHT_TOO_HIGH
        if (windKnots > 40) return Warning.EXTREME_WIND
        if (windKnots < 12) return Warning.LOW_WIND
        if (windKnots < 15 && (skill.key.contains("medium") || skill.key.contains("large"))) {
            return Warning.INSUFFICIENT_WIND_WAVES
        }
        return null
    }

    /**
     * Result, or null when [validate] reports a warning - the web shows either the
     * warning or the result, never both.
     */
    fun calculate(windKnots: Double?, weightKg: Double?, skill: SkillLevel?): Result? {
        if (validate(windKnots, weightKg, skill) != null) return null
        return Result(kiteSize(windKnots!!, weightKg!!, skill), boardSize(weightKg, skill!!))
    }

    private fun jsRound(value: Double): Int = floor(value + 0.5).toInt()
}
