package surf.varun.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Pure Kotlin module with no Android dependency (core:model, core:domain). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureJava()
        tasks.withType<Test>().configureEach { useJUnit() }
        dependencies {
            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
        }
    }
}
