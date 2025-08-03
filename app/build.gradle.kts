plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}


android {
    namespace = "com.example.ibm_project"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ibm_project"
        minSdk = 24  // ARCore 最低要求
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // 保持 Filament 檔案壓縮 (重要!)
    androidResources {
        noCompress += listOf("filamat", "ktx")
    }

    // ARCore 相關配置
    packaging {
        resources {
            pickFirsts += listOf("**/libc++_shared.so", "**/libjsc.so")
        }
    }
}

dependencies {
    // ===== SceneView（簡化版 - 快速開發）=====
    implementation("io.github.sceneview:arsceneview:2.3.0")  // AR + 3D
    implementation("io.github.sceneview:sceneview:2.3.0")    // 純 3D

    // ===== 原生 Filament 1.5.6（完全控制版）=====
    implementation("com.google.ar:core:1.41.0")
    implementation("com.google.android.filament:filament-android:1.5.6")
    implementation("com.google.android.filament:gltfio-android:1.5.6")
    implementation("com.google.android.filament:filament-utils-android:1.5.6")

    // Android 核心依賴
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ===== 網路 API 依賴 =====
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 圖片載入
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ViewModel 和 LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // 協程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 數學運算 (3D 計算)
    implementation("dev.romainguy:kotlin-math:1.5.3")

    // 權限處理
    implementation("pub.devrel:easypermissions:3.0.0")

    // 測試依賴
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

        implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")






}