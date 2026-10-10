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
        maven("https://jitpack.io")
        maven("https://jogamp.org/deployment/maven")
        maven("https://jitpack.io")
    }
}

rootProject.name = "Reader"

fun isDesktopOnlyBuild(): Boolean {
    providers.gradleProperty("desktopOnly").orNull
        ?.let { return it.equals("true", ignoreCase = true) }

    val requestedTasks = gradle.startParameter.taskNames
    return requestedTasks.isNotEmpty() && requestedTasks.all { taskName ->
        val normalized = taskName.removePrefix(":")
        normalized.startsWith("desktopApp:")
    }
}

if (!isDesktopOnlyBuild()) {
    include(":app")
}
include(":shared")
include(":desktopApp")
