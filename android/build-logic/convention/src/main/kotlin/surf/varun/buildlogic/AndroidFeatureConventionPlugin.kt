package surf.varun.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module: an Android library with Compose and Hilt that may depend on
 * core modules only - never on another feature.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("varun.android.library")
        pluginManager.apply("varun.android.compose")
        pluginManager.apply("varun.hilt")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:data"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:navigation"))

            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-compose-material-icons-extended"))
            add("implementation", libs.library("kotlinx-collections-immutable"))
            add("implementation", libs.library("androidx-navigation3-runtime"))
            add("implementation", libs.library("androidx-activity-compose"))
            add("implementation", libs.library("timber"))

            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.library("turbine"))
        }
    }
}
