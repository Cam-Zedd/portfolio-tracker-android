plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.zeddcara.portfoliotracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.zeddcara.portfoliotracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1-v5.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
