plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.resultatsbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.resultatsbridge.v2"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("onnx", "tflite")
    }
}

dependencies {

    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.ui:ui-text:1.6.0")
    implementation("androidx.compose.foundation:foundation:1.6.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ✅ Ajout pour le serveur HTTP
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui.text)
    implementation(libs.runtime)
    implementation(libs.androidx.compose.runtime.runtime)
    implementation(libs.ui.graphics)
    implementation(libs.androidx.runtime.saveable)
    implementation(libs.androidx.compose.material3.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("com.google.code.gson:gson:2.10.1")

    // Bibliothèque d'icônes de base (obligatoire pour ArrowBack)
    implementation("androidx.compose.material:material-icons-core")

    // Si vous utilisez d'autres icônes plus complexes
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
