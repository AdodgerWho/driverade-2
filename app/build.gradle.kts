plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.driverade"
    compileSdk = 34

    signingConfigs {
        create("driveradeUpdates") {
            val keyPath = System.getenv("DRIVERADE_KEYSTORE_PATH")
            if (!keyPath.isNullOrBlank()) {
                storeFile = file(keyPath)
                storePassword = System.getenv("DRIVERADE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DRIVERADE_KEY_ALIAS")
                keyPassword = System.getenv("DRIVERADE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.driverade"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("DRIVERADE_VERSION_CODE")?.toIntOrNull() ?: 62
        versionName = "3.64"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            if (!System.getenv("DRIVERADE_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("driveradeUpdates")
            }
        }
    }

    buildFeatures {
        viewBinding = true
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
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
