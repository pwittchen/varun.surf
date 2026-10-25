import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.versions)
}

// Same rules as the backend's build.gradle: the DEPS workflow reads the report
// and bumps gradle/libs.versions.toml, taking stable releases within the
// current major only. Date-versioned artifacts (the Compose BOM) roll their
// "major" every year, so the major check skips them.
fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    return !stableKeyword && !Regex("^[0-9,.v-]+(-r)?$").matches(version)
}

fun isMajorUpdate(candidate: String, current: String): Boolean {
    val candidateMajor = candidate.substringBefore('.').toIntOrNull() ?: return false
    val currentMajor = current.substringBefore('.').toIntOrNull() ?: return false
    return currentMajor < 1000 && candidateMajor > currentMajor
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        (isNonStable(candidate.version) && !isNonStable(currentVersion)) ||
            isMajorUpdate(candidate.version, currentVersion)
    }
    outputFormatter = "json,plain"
}
