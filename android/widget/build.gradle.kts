plugins {
    alias(libs.plugins.varun.android.feature)
}

android {
    namespace = "surf.varun.widget"
}

dependencies {
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
