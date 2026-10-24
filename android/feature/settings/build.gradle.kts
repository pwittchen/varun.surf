plugins {
    alias(libs.plugins.varun.android.feature)
}

android {
    namespace = "surf.varun.feature.settings"
}

dependencies {
    implementation(libs.androidx.appcompat)
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
