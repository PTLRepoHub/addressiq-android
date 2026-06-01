plugins {
    id("com.android.library") version "8.5.0"
    kotlin("android") version "2.0.0"
    // Compose compiler plugin — Kotlin 2.0+ ships it standalone.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    // @Parcelize for the verify-flow contract types (ui/AddressDraft.kt, ui/AddressIQVerifyContract.kt).
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.0"
    `maven-publish`
    signing
}

// Version is injected by CI from the release git tag (`-PVERSION=1.2.3`);
// falls back to a SNAPSHOT for local builds. See .github/workflows/release.yml.
version = (project.findProperty("VERSION") as String?) ?: "0.0.0-SNAPSHOT"
group = "com.addressiq.android"

android {
    namespace = "com.addressiq.android"
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
    // Modern production target — Android 16 / API 36. Honors the latest
    // foreground-service-type, runtime-notification, and split-permission
    // mandates. See docs/sdk-contract.md §0.5 for the support policy.
    compileSdk = 36
    defaultConfig {
        // Minimum supported — Android 7 / API 24 (Nougat, 2016). Below
        // this, the runtime-permission model + foreground-service
        // formalism aren't reliable enough to honor the cross-SDK
        // contract. Partners on older devices should treat verification
        // confidence as best-effort; weak signal resolves to UNKNOWN.
        minSdk = 24
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        // Powers the `AddressIQVerify` activity (ui/ package) — full
        // themed collect + verify flow on top of the AddressIQ singleton.
        compose = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    // Provides `CoroutineScope.future { ... }` — exposes Kotlin suspend
    // functions to Java callers as CompletableFuture. Used by
    // AddressIQJava (java/AddressIQJavaBridge.kt). CompletableFuture
    // requires API 24+ which matches our minSdk.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    // SQLCipher's SQLiteDatabase implements androidx.sqlite's SupportSQLiteDatabase —
    // required on the compile classpath (TelemetryQueue.kt).
    implementation("androidx.sqlite:sqlite:2.4.0")
    // Tink — Keystore-backed AEAD for SecureKeyValueStore.kt.
    implementation("com.google.crypto.tink:tink-android:1.13.0")
    // ContextCompat / ActivityCompat for permission checks.
    implementation("androidx.core:core-ktx:1.13.1")
    // ComponentActivity + ActivityResultRegistry for SDK-owned permission
    // requests via ActivityResultContracts (see permissions/PermissionRequester.kt).
    implementation("androidx.activity:activity-ktx:1.9.0")

    // ── Compose (powers the AddressIQVerify activity + theme system) ──
    // BOM keeps Compose libraries on a coherent version set.
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // For ViewModel-scoped state inside the verify activity if needed.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // ── JVM unit tests (./gradlew test) ──
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.addressiq.android"
            artifactId = "sdk"
            // version is taken from the project version set above (CI tag).

            // The android library component only exists after evaluation.
            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("AddressIQ Android SDK")
                description.set("AddressIQ address collection + verification SDK for Android.")
                url.set("https://addressiq.io")
                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://addressiq.io/license")
                    }
                }
                developers {
                    developer {
                        id.set("addressiq")
                        name.set("AddressIQ")
                    }
                }
                scm {
                    url.set("https://github.com/addressiq/geo-tagging")
                }
            }
        }
    }

    repositories {
        // GitHub Packages — always available in CI via the automatic GITHUB_TOKEN.
        maven {
            name = "GitHubPackages"
            url = uri(
                System.getenv("GITHUB_REPOSITORY")
                    ?.let { "https://maven.pkg.github.com/$it" }
                    ?: "https://maven.pkg.github.com/addressiq/geo-tagging",
            )
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }

        // Maven Central (Sonatype OSSRH) — only wired when the OSSRH secrets
        // are present, so local builds and PR CI don't fail on missing creds.
        if (!System.getenv("OSSRH_USERNAME").isNullOrBlank()) {
            maven {
                name = "MavenCentral"
                url = uri(
                    if (version.toString().endsWith("SNAPSHOT")) {
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    } else {
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                    },
                )
                credentials {
                    username = System.getenv("OSSRH_USERNAME")
                    password = System.getenv("OSSRH_PASSWORD")
                }
            }
        }
    }
}

// GPG signing is required for Maven Central but optional elsewhere. Only
// configured when an in-memory signing key is provided (CI release job).
signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
