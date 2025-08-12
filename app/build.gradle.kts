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
        minSdk = 24  // ARCore minimum requirement
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

    // Keep Filament file compression (Important!)
    androidResources {
        noCompress += listOf("filamat", "ktx")
    }

    // ARCore related configuration + META-INF conflict resolution
    packaging {
        resources {
            pickFirsts += listOf("**/libc++_shared.so", "**/libjsc.so")
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt", 
                "META-INF/LICENSE",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE",
                "META-INF/ASL2.0",
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // ===== SceneView (Simplified version - Rapid development) =====
    implementation("io.github.sceneview:arsceneview:2.3.0")  // AR + 3D
    implementation("io.github.sceneview:sceneview:2.3.0")    // Pure 3D

    // ===== Native Filament 1.5.6 (Full control version) =====
    implementation("com.google.ar:core:1.41.0")
    implementation("com.google.android.filament:filament-android:1.5.6")
    implementation("com.google.android.filament:gltfio-android:1.5.6")
    implementation("com.google.android.filament:filament-utils-android:1.5.6")

    // Android core dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Compose BOM and core dependencies - Use BOM for unified version management
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")  // Material 2
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // ===== Material Icons - Let BOM manage version =====
    implementation("androidx.compose.material:material-icons-extended")  // Remove version number, let BOM control

    // ===== Network API dependencies =====
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Mathematical operations (3D calculations)
    implementation("dev.romainguy:kotlin-math:1.5.3")

    // Permission handling
    implementation("pub.devrel:easypermissions:3.0.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // ===== Unit Testing =====
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")  // Android Unit Tests
    testImplementation("io.mockk:mockk:1.13.8")  // Kotlin Mocking
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")  // Coroutine Testing
    testImplementation("androidx.arch.core:core-testing:2.2.0")  // LiveData Testing
    testImplementation("app.cash.turbine:turbine:1.0.0")  // Flow Testing
    testImplementation("com.google.truth:truth:1.1.4")  // Better Assertions
    testImplementation("org.mockito:mockito-core:5.7.0")  
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")  
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")  // Mock HTTP Server
    
    // ===== Android Integration Testing =====
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")  // Intent Testing
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")  // Coroutine Testing for Android Tests
    
    // ===== Compose Testing =====
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")  // BOM manages version
    debugImplementation("androidx.compose.ui:ui-test-manifest")     
    debugImplementation("androidx.compose.ui:ui-tooling")          
}