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

rootProject.name = "addressiq-android-kotlin-example"

// Local link to the SDK at the repo root via a composite build. Gradle
// substitutes the published coordinate `com.addressiq.android:sdk` with the
// local build, so the example tracks the SDK source with no publish step.
// To test against the PUBLISHED artifact instead, delete this block — the
// mavenCentral() repo above resolves `com.addressiq.android:sdk:<version>`.
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.addressiq.android:sdk")).using(project(":"))
    }
}
