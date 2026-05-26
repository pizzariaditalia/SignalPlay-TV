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
    
    // 🎬 O Motor do Player de Vídeo Nativo
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    
    // Imagens
    implementation("com.github.bumptech.glide:glide:4.15.1")
    
    // Firebase e API
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
