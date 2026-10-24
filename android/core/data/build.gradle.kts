plugins {
    alias(libs.plugins.varun.android.library)
    alias(libs.plugins.varun.hilt)
}

android {
    namespace = "surf.varun.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.timber)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
}
