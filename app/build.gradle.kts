plugins {
    id("com.android.application")
}

android {
    namespace = "com.tahen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tahen.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}
