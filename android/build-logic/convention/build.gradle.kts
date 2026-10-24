plugins {
    `kotlin-dsl`
}

group = "surf.varun.buildlogic"


dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "varun.android.application"
            implementationClass = "surf.varun.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "varun.android.library"
            implementationClass = "surf.varun.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "varun.android.compose"
            implementationClass = "surf.varun.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "varun.android.feature"
            implementationClass = "surf.varun.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "varun.hilt"
            implementationClass = "surf.varun.buildlogic.HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "varun.jvm.library"
            implementationClass = "surf.varun.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
