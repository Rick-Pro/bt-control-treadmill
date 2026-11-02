pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google { content { includeGroupByRegex("com\\.android.*|com\\.google.*|androidx.*") } }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google { content { includeGroupByRegex("androidx.*|com\\.android.*|com\\.google\\.android.*") } }
    }
}
rootProject.name = "bt-control-treadmill"
include(":core")
// The Android app module needs the Android SDK. Agent sandboxes do not have it, so it is only included when an SDK exists.
// Agents run: ./gradlew -Pstride.coreOnly=true :core:test
val sdkHere = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || file("local.properties").exists()
val coreOnly = providers.gradleProperty("stride.coreOnly").isPresent
if (sdkHere && !coreOnly && file("app/build.gradle.kts").exists()) include(":app")
