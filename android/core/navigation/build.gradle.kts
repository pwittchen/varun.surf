plugins {
    alias(libs.plugins.varun.android.library)
    alias(libs.plugins.varun.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "surf.varun.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.json)
}
