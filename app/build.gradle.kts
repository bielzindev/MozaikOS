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
    }

    // One fixed key for every build: Android only accepts an update signed with the same key as
    // the installed app, and the default debug key changes on every CI runner. The key is not in
    // the repository; CI writes it from the ZUNEX_KEYSTORE_BASE64 secret to $ZUNEX_KEYSTORE.
    val zunexKeystore = System.getenv("ZUNEX_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        create("zunex") {
            if (zunexKeystore != null) {
                storeFile = zunexKeystore
                // A missing secret reaches the build as an empty variable: treat it as unset.
                fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
                storePassword = env("ZUNEX_KEYSTORE_PASSWORD") ?: "zunex-release"
                keyAlias = env("ZUNEX_KEY_ALIAS") ?: "zunex"
                keyPassword = env("ZUNEX_KEY_PASSWORD") ?: env("ZUNEX_KEYSTORE_PASSWORD") ?: "zunex-release"
            } else {
                initWith(getByName("debug"))
            }
        }
    }

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
            signingConfig = signingConfigs.getByName("zunex")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("zunex")
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
    debugImplementation("androidx.compose.ui:ui-tooling")
}
