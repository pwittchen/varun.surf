plugins {
    alias(libs.plugins.varun.android.library)
    alias(libs.plugins.varun.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "surf.varun.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.turbine)
}
