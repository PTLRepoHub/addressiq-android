plugins {
    // Pure Java app — no Kotlin/Compose plugins needed to consume the (Kotlin) SDK.
    id("com.android.application") version "8.5.0"
}

android {
    namespace = "com.addressiq.example.java"
    // Must be >= the SDK's compileSdk (36).
    compileSdk = 36
    defaultConfig {
        applicationId = "com.addressiq.example.java"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Resolved from the local SDK via the composite build in settings.gradle.kts.
    implementation("com.addressiq.android:sdk")
    // ComponentActivity + registerForActivityResult, needed to launch the
    // Collect UI (AddressIQVerifyContract) from Java. Matches the SDK's version.
    implementation("androidx.activity:activity:1.9.0")
}
