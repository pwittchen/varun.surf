plugins {
    alias(libs.plugins.varun.android.feature)
}

android {
    namespace = "surf.varun.feature.alerts"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
