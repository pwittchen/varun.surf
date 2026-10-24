plugins {
    alias(libs.plugins.varun.android.library)
    alias(libs.plugins.varun.android.compose)
}

android {
    namespace = "surf.varun.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.domain)
    api(libs.androidx.compose.material.icons.extended)
}
