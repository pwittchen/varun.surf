package surf.varun.core.model

/** The calculator's skill & conditions options, keyed like the web's `<select>`. */
enum class SkillLevel(val key: String) {
    BEGINNER_FLAT("beginner-flat"),
    BEGINNER_SMALL("beginner-small"),
    INTERMEDIATE_FLAT("intermediate-flat"),
    INTERMEDIATE_MEDIUM("intermediate-medium"),
    ADVANCED_FLAT("advanced-flat"),
    ADVANCED_MEDIUM("advanced-medium"),
    ADVANCED_LARGE("advanced-large");

    companion object {
        fun fromKey(key: String?): SkillLevel? = entries.firstOrNull { it.key == key }
    }
}

data class CalculatorInputs(
    val windKnots: Double? = null,
    val weightKg: Double? = null,
    val skill: SkillLevel? = null,
)
