pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "varun-surf-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:domain")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:navigation")
include(":core:testing")
include(":feature:spots")
include(":feature:spot")
include(":feature:map")
include(":feature:search")
include(":feature:calculator")
include(":feature:alerts")
include(":feature:settings")
include(":feature:about")
include(":widget")
include(":benchmark")
