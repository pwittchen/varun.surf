plugins {
    alias(libs.plugins.varun.android.library)
    alias(libs.plugins.varun.android.compose)
}

android {
    namespace = "surf.varun.core.ui"
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    api(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.navigation)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
