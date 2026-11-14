pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "VintageVinyl"

// :dsp      pure-Kotlin audio engine (no Android dependencies, unit-tested on the JVM)
// :domain   pure-Kotlin models, repository contracts and use cases
// :app      Android application (Compose UI, Media3 playback, data layer)
include(":dsp", ":domain", ":app")
