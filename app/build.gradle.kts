plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.athena.creamkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.athena.creamkeyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Release builds are intentionally unsigned; sign locally or through a protected CI secret.
        }
    }
}
