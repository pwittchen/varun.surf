plugins {
    alias(libs.plugins.varun.android.feature)
}

android {
    namespace = "surf.varun.feature.map"
}

dependencies {
    implementation(libs.maplibre.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
