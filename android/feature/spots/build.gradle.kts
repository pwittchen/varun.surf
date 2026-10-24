plugins {
    alias(libs.plugins.varun.android.feature)
}

android {
    namespace = "surf.varun.feature.spots"
}

dependencies {
    implementation(libs.androidx.navigation3.ui)
}
