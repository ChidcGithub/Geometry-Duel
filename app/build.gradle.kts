plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.geometryduel"
    compileSdk = 37

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("geometry-duel.jks")
            storePassword = "android"
            keyAlias = "geometryduel"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.geometryduel"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "3.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
}
