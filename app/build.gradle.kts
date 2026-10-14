plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.onthego"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.onthego"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.0"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments.add("-DCMAKE_BUILD_TYPE=Release")
                cppFlags.add("-O3")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.security:security-crypto:1.1.0")
    // VAD (Phase 2): uncomment when adding model file
    // implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
}
