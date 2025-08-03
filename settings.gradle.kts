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
        // SceneView 需要的倉庫
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "YourApp"
include(":app")