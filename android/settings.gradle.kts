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
    }
}

rootProject.name = "mtg-reforged"

// Upstream Forge modules, compiled from ../<module>/src (see buildSrc/forge-engine-module).
listOf("forge-core", "forge-game", "forge-ai", "forge-gui").forEach { include(":engine:$it") }
include(":app")
// The Windows app: same shared code (shared/), Compose for Desktop instead of Android.
include(":desktop")
