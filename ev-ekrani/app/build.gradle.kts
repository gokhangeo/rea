plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gokhan.evekrani"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gokhan.evekrani"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // Her sürüm aynı anahtarla imzalanmalı ki yeni APK eskisinin üstüne kurulabilsin. Anahtar depoda tutulmaz:
    // GitHub'da EV_KEYSTORE_B64 ve EV_KEYSTORE_PASS gizli değerleri varsa onlar, yoksa derleme makinesinin
    // önbellekte saklanan hata ayıklama anahtarı kullanılır (bkz. .github/workflows/ev-ekrani.yml).
    val keystore = rootProject.file("ev-ekrani.jks")
    val keystorePass = System.getenv("EV_KEYSTORE_PASS")
    if (keystore.exists() && !keystorePass.isNullOrEmpty()) {
        signingConfigs.create("ev") {
            storeFile = keystore
            storePassword = keystorePass
            keyAlias = System.getenv("EV_KEY_ALIAS") ?: "evekrani"
            keyPassword = keystorePass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("ev") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
