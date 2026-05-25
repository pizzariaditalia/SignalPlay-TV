plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tv.signalplay"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tv.signalplay"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    
    // O motor do banco de dados do seu Painel ADM
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.0")
}
