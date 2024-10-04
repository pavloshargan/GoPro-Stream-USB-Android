plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.streamtest"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.streamtest"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        jniLibs.pickFirsts.add("**/libc++_shared.so")
    }
}

dependencies {
    implementation("com.google.android.exoplayer:exoplayer-ui:2.17.1") // for UI components like PlayerView
    implementation("com.google.android.exoplayer:exoplayer-core:2.17.1")
    implementation("com.arthenica:ffmpeg-kit-full-gpl:5.1")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")
    implementation("androidx.activity:activity-compose:1.7.0")
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.material3.android)

}