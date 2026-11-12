pluginManagement {
    includeBuild("build-logic")
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor y nanojson se publican en JitPack
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") }
        }
    }
}

rootProject.name = "OpenMusicStreaming"

include(":app")
include(":core:model")
include(":core:navigation")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:extractor")
include(":core:data")
include(":core:media")
include(":core:download")
include(":feature:library")
include(":feature:streaming")
include(":feature:player")
include(":feature:bookmarks")
include(":feature:settings")
