// Repositories for the Android Gradle Plugin + library dependencies. Declared
// here (not in build.gradle.kts) so the SDK builds standalone — `gradle test`
// in CI and composite builds both rely on this.
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

// Names the SDK's root project so composite builds (and the example app's
// dependency substitution) can refer to it as `com.addressiq.android:sdk`.
rootProject.name = "sdk"
