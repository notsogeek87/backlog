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
        // lielugit-updater : dépôt Maven vendoré (issu du zip de la release), versionné dans git — aucun jeton requis.
        maven {
            url = uri("$rootDir/libs/lielugit-maven")
            content { includeGroup("com.lielu") }
        }
    }
}

rootProject.name = "Backlog"
include(":app")
