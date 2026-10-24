pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.5.2"
        id("org.jetbrains.kotlin.android") version "1.9.24"
        id("com.google.gms.google-services") version "4.4.2"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SVGA player (YY ki .svga animation files) JitPack se aata hai
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VoiceLudoApp"
include(":app")
