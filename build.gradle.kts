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
// Maven coordinate group — must match the publication's groupId below and the
// Central Portal namespace (com.addressiqpro, verified via addressiqpro.com).
// Distinct from the Android/Kotlin package `com.addressiq.android`.
group = "com.addressiqpro.android"

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Production API base URL, baked into the compiled AAR bytecode at
        // build time. CI injects the real value via `-PaddressiqApiUrl=...`
        // (from the GitHub `ADDRESSIQ_API_URL` variable); local/default builds
        // fall back to the public URL. Consumed by
        // AddressIQEnvironment.PRODUCTION.defaultApiUrl().
        buildConfigField(
            "String",
            "ADDRESSIQ_API_URL",
            "\"${project.findProperty("addressiqApiUrl") ?: "https://api.addressiqpro.com"}\"",
        )
        // Dedicated transit-event ingest host, baked into the compiled AAR at
        // build time. CI injects the real value via `-PaddressiqIngestUrl=...`
        // (from the GitHub `ADDRESSIQ_INGEST_URL` variable); local/default
        // builds fall back to the public URL. Consumed by
        // AddressIQEnvironment.PRODUCTION.defaultIngestUrl().
        buildConfigField(
            "String",
            "ADDRESSIQ_INGEST_URL",
            "\"${project.findProperty("addressiqIngestUrl") ?: "https://ingest-api.addressiqpro.com"}\"",
        )
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
        // Emits the generated BuildConfig class so the production API URL can
        // be baked into the compiled AAR (see ADDRESSIQ_API_URL below).
        buildConfig = true
    }
}

dependencies {
    // Runtime for the generated wire-contract bindings under
    // src/main/java + src/main/kotlin (source: PTLRepoHub/AddressIq-proto).
    // The "lite" runtime keeps method count / APK size down on Android and
    // bundles the google.protobuf well-known types (e.g. Timestamp).
    implementation("com.google.protobuf:protobuf-kotlin-lite:3.25.3")

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
    // Provides the XML `Theme.Material3.*` parents used by res/values/themes.xml
    // (the verify activity's window theme). Compose-material3 does not ship these.
    implementation("com.google.android.material:material:1.12.0")
    // For ViewModel-scoped state inside the verify activity if needed.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // ── JVM unit tests (./gradlew test) ──
    testImplementation("junit:junit:4.13.2")
    // Real org.json impl so JVM unit tests can parse bridge JSON (the android.jar
    // stub throws "not mocked" for org.json).
    testImplementation("org.json:json:20240303")

    // ── Instrumented tests (./gradlew connectedDebugAndroidTest) ──
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            // Maven Central namespaces are granted on proof of domain ownership.
            // We own addressiqpro.com, so the namespace is com.addressiqpro.
            // (This is the Maven coordinate only — the Java/Kotlin package
            // remains com.addressiq.android.)
            groupId = "com.addressiqpro.android"
            artifactId = "sdk"
            // version is taken from the project version set above (CI tag).

            // The android library component only exists after evaluation.
            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("AddressIQ Android SDK")
                description.set("AddressIQ address collection + verification SDK for Android.")
                url.set("https://addressiqpro.com")
                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://addressiqpro.com/license")
                    }
                }
                developers {
                    developer {
                        id.set("addressiq")
                        name.set("AddressIQ")
                    }
                }
                scm {
                    url.set("https://github.com/PTLRepoHub/addressiq-android")
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
                    ?: "https://maven.pkg.github.com/PTLRepoHub/addressiq-android",
            )
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }

        // Maven Central via the Sonatype Central Portal.
        //
        // The legacy OSSRH hosts (oss.sonatype.org, s01.oss.sonatype.org) were
        // sunset on 2025-06-30. We publish through the Portal's OSSRH Staging
        // API, a Nexus-2 compatibility shim that plain `maven-publish` can talk
        // to. Credentials are Central Portal *user tokens*, not OSSRH logins.
        //
        // ⚠ Uploading is not enough. With plain `maven-publish` the deployment
        // sits in a repository that is never handed to the Portal until you
        // POST /manual/upload/defaultRepository/<namespace> — from the SAME IP
        // that uploaded. release.yml does this. Without it, nothing appears at
        // https://central.sonatype.com/publishing and the release silently
        // never happens.
        //
        // Only wired when the token is present, so local builds and PR CI don't
        // fail on missing creds.
        if (!System.getenv("CENTRAL_TOKEN_USERNAME").isNullOrBlank()) {
            maven {
                name = "MavenCentral"
                url = uri(
                    if (version.toString().endsWith("SNAPSHOT")) {
                        "https://central.sonatype.com/repository/maven-snapshots/"
                    } else {
                        "https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/"
                    },
                )
                credentials {
                    username = System.getenv("CENTRAL_TOKEN_USERNAME")
                    password = System.getenv("CENTRAL_TOKEN_PASSWORD")
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
