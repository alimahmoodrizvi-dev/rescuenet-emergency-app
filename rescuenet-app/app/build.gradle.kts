plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-kapt")
}

android {
    namespace = "com.rescuenet.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rescuenet.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-phase10-competition-polish"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")

    // DI (wired up starting Phase 3 — declared now so screens/viewmodels are DI-ready)
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Local persistence (Phase 3) — SQLCipher-encrypted at rest as of Phase 10, see di/DatabaseModule.kt
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Keystore-backed encrypted storage for the DB passphrase (Phase 10)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Offline sync scheduling (Phase 3)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Settings persistence (Phase 3)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Location (Phase 3) — FusedLocationProvider gives battery-friendlier fixes than raw GPS
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Serialization for queued event payloads
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Backend API client (Phase 5)
    implementation(platform("com.squareup.retrofit2:retrofit-bom:2.11.0"))
    implementation("com.squareup.retrofit2:retrofit")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests (Phase 9) — plain JVM, no Android framework/Robolectric needed for the
    // pure-logic classes under test (OfflineAiHeuristics, IncidentSummaryMerger,
    // MeshMessageCrypto, MeshWireFormat). See TESTING.md for what is and isn't covered here.
    testImplementation("junit:junit:4.13.2")
}
