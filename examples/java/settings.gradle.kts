pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "addressiq-android-java-example"

// Local link to the SDK at the repo root via a composite build. Gradle
// substitutes the published coordinate `com.addressiq.android:sdk` with the
// local build, so the example tracks the SDK source with no publish step.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.addressiq.android:sdk")).using(project(":"))
    }
}
