plugins {
    id("com.android.application")
}

android {
    namespace = "app.spamblocker"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.spamblocker"
        minSdk = 29 // call screening role needs Android 10
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
