package surf.varun.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        dependencies {
            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
        }
    }
}
