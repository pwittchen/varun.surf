plugins {
    alias(libs.plugins.varun.android.application)
    alias(libs.plugins.varun.android.compose)
    alias(libs.plugins.varun.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "surf.varun.app"

    defaultConfig {
        // Derived from the owned domain; permanent once uploaded to Play (spec D2)
        applicationId = "surf.varun.app"
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("varun.versionName").getOrElse("1.0.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystore = providers.environmentVariable("VARUN_KEYSTORE_FILE").orNull
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("VARUN_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("VARUN_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("VARUN_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (providers.environmentVariable("VARUN_KEYSTORE_FILE").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures.buildConfig = true

    androidResources {
        generateLocaleConfig = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)
    implementation(projects.feature.spots)
    implementation(projects.feature.spot)
    implementation(projects.feature.map)
    implementation(projects.feature.search)
    implementation(projects.feature.calculator)
    implementation(projects.feature.alerts)
    implementation(projects.feature.settings)
    implementation(projects.feature.about)
    implementation(projects.widget)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.benchmark)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
