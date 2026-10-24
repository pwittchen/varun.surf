plugins {
    alias(libs.plugins.varun.android.feature)
}

android {
    namespace = "surf.varun.feature.spot"
}

dependencies {
    implementation(libs.coil.compose)
    // Static mini map of the spot location (MapSnapshotter)
    implementation(libs.maplibre.android)

    testImplementation(libs.robolectric)
}
