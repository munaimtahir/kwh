plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.kwh" // change if your package differs
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.kwh" // change if needed
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = false
            // proguardFiles(
            //     getDefaultProguardFile("proguard-android-optimize.txt"),
            //     "proguard-rules.pro"
            // )
        }
    }

    buildFeatures { compose = true }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Enable core library desugaring so that java.time and other
        // modern Java APIs are available on older API levels.  Without
        // this flag, using classes like java.time.Instant will cause
        // build failures on API < 26.  See the Android docs for details.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Compose BOM keeps versions aligned.  The previously referenced
    // 2024.10.01 version does not exist in the public repositories and
    // caused dependency resolution errors.  The latest stable BOM
    // available as of September 2025 is 2025.09.01【199518996393075†L94-L104】.
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI + Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.material3:material3")

    // ✅ Missing pieces that your code references:
    implementation("androidx.compose.foundation:foundation")                 // KeyboardOptions, etc.
    implementation("androidx.compose.material:material-icons-extended")      // CloudUpload/CloudDownload/History icons

    // Activity + Navigation (Compose)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Material Components (XML themes/attrs)
    implementation("com.google.android.material:material:1.12.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room (if used)
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Lifecycle integrations for Compose.  These libraries provide
    // collectAsStateWithLifecycle and ViewModel support for Compose
    // components.  Without them, compilation fails due to missing
    // symbols when using collectAsStateWithLifecycle in the UI code.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Core library desugaring dependency.  Paired with
    // isCoreLibraryDesugaringEnabled in compileOptions, this pulls in
    // the desugared JDK libraries to support Java 8+ APIs such as
    // java.time on older Android versions.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
