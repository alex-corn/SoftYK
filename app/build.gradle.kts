plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.corn.softyk"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.corn.softyk"
        minSdk = 28  // Android 9 — minimum for StrongBox API
        targetSdk = 37
        versionCode = 1001001
        versionName = "1.0.1"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.material:material:1.11.0")
}
