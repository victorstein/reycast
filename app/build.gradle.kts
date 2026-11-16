plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.victorstein.reycast"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.victorstein.reycast"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
