package surf.varun.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true

        extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
            // Compose compiler reports on demand: ./gradlew assembleRelease -PcomposeReports
            if (providers.gradleProperty("composeReports").isPresent) {
                reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
            }
        }

        dependencies {
            val bom = libs.library("androidx-compose-bom")
            add("implementation", platform(bom))
            add("testImplementation", platform(bom))
            add("implementation", libs.library("androidx-compose-ui"))
            add("implementation", libs.library("androidx-compose-foundation"))
            add("implementation", libs.library("androidx-compose-material3"))
            add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        }
    }
}
