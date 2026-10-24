plugins {
    alias(libs.plugins.varun.android.library)
}

android {
    namespace = "surf.varun.core.testing"
}

dependencies {
    api(projects.core.model)
    api(projects.core.data)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
