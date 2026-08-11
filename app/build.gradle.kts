plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.turbobar.ime"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.turbobar.ime"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.2-native"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Lifecycle — needed to build the custom LifecycleOwner/ViewModelStoreOwner/
    // SavedStateRegistryOwner bridge, since InputMethodService doesn't provide
    // these the way an Activity does.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // QR generation — using the industry-standard ZXing library rather than
    // a hand-ported encoder, now that this is a real Gradle project and can
    // just depend on a mature, battle-tested implementation directly.
    implementation("com.google.zxing:core:3.5.3")
}
