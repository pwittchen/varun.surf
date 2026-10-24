plugins {
    alias(libs.plugins.varun.android.library)
    alias(libs.plugins.varun.hilt)
}

android {
    namespace = "surf.varun.core.datastore"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.turbine)
}
