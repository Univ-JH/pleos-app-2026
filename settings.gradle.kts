pluginManagement {
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
        maven {
            name = "PleosPlayground"
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content {
                includeGroupByRegex("ai\\.pleos\\.playground.*")
            }
        }
    }
}

rootProject.name = "PleosApp2026"
include(":app")
