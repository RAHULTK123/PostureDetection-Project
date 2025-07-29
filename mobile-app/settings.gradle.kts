pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { // <-- Add this block for JitPack
            url = uri("https://jitpack.io")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { // <-- Ensure this is here
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "Posture-Detection-App"
include(":app")
 