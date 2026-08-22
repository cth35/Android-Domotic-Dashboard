plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.homehabit.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.homehabit.app"
        // Version 24 required for rtsp-client-android (low-latency MediaCodec)
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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

    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")

    // Dashboard JSON config
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Domoticz Client (API /json.htm)
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-auth:2.3.12")

    // Domoticz real-time websocket channel (/json): Direct OkHttp instead
    // of Ktor websocket plugin, not to touch the existing Android
    // engine of DomoticzClient (REST) which already works.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Embedded HTTP server (config edition from a browser)
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-cio:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")

    // Camera: RTSP stream (libVLC) + snapshot poster loading (Coil)
    implementation("org.videolan.android:libvlc-all:3.7.0")
    implementation("com.github.alexeyvasilyev:rtsp-client-android:5.3.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Font Awesome icons (to activate in the next step)
    // implementation("com.mikepenz:iconics-core:5.4.0")
    // implementation("com.mikepenz:iconics-compose:5.4.0")
    // implementation("com.mikepenz:google-font-awesome-typeface:6.5.1.0-kotlin@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")
}