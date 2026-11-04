plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wpreborn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wpreborn.launcher"
        minSdk = 29
        targetSdk = 35
        // Every CI build gets a higher number, so a new APK installs over the previous one.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
        // Phones are ARM. The x86_64 emulator code only goes into debug builds: translation, OCR
        // and the map engine ship native code per ABI, and a third ABI made the phone APK so big
        // that installers rejected it as an invalid package.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // One fixed key for every build: Android only accepts an update signed with the same key as
    // the installed app, and the default debug key changes on every CI runner. The key is not in
    // the repository; CI writes it from the ZUNEX_KEYSTORE_BASE64 secret to $ZUNEX_KEYSTORE.
    val zunexKeystore = System.getenv("ZUNEX_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (zunexKeystore != null) create("zunex") {
            // A missing secret reaches the build as an empty variable: treat it as unset.
            fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            storeFile = zunexKeystore
            storePassword = env("ZUNEX_KEYSTORE_PASSWORD") ?: "zunex-release"
            keyAlias = env("ZUNEX_KEY_ALIAS") ?: "zunex"
            keyPassword = env("ZUNEX_KEY_PASSWORD") ?: env("ZUNEX_KEYSTORE_PASSWORD") ?: "zunex-release"
        }
    }
    // Without the key (local builds, the emulator check) the standard debug key signs.
    val signing = if (zunexKeystore != null) signingConfigs.getByName("zunex") else signingConfigs.getByName("debug")

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "LITE", "false")
        }
        // No SMS / call log / direct-call permissions: Play Protect friendly. Messaging and the
        // call history hand over to the Android apps instead.
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "LITE", "true")
        }
    }

    buildTypes {
        debug {
            signingConfig = signing
            ndk { abiFilters += "x86_64" }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-extensions:$camerax")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // On-device AI for the camera (scene recognition, portrait segmentation); bundled models.
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    // News: reading view (main text of an article page); MIT licence.
    implementation("org.jsoup:jsoup:1.18.1")
    // Translator: on-device translation, language detection and text in photos (ML Kit).
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    // Play Services variant: the OCR model is downloaded by Google Play services, not bundled.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("org.maplibre.gl:android-sdk:11.8.8")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
