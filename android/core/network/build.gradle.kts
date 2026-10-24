plugins {
    alias(libs.plugins.varun.android.library)
    alias(libs.plugins.varun.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "surf.varun.core.network"
    buildFeatures.buildConfig = true
    defaultConfig {
        // Backend the app talks to; point a debug build at a local server with
        // ./gradlew installDebug -Pvarun.baseUrl=http://10.0.2.2:8080
        val baseUrl = providers.gradleProperty("varun.baseUrl").getOrElse("https://varun.surf")
        buildConfigField("String", "BASE_URL", "\"${baseUrl.trimEnd('/')}/\"")
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.domain)
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
