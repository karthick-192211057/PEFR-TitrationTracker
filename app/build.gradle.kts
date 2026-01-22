plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    // Apply kapt directly by its ID to resolve the conflict
    id("org.jetbrains.kotlin.kapt")

    // This is for navigation (already correct)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
    // If you want `google-services` plugin enablement, add it to the root Gradle plugins instead.
}

android {
    namespace = "com.example.pefrtitrationtracker"

    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pefrtitrationtracker"
        minSdk = 24
        targetSdk = 34
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // ADDED to enable ViewBinding
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // --- Core UI ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // --- ADDED: Navigation ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- ADDED: Lifecycle ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- ADDED: Networking ---
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)

    // --- ADDED: Coroutines ---
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // --- Test Dependencies ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.mpandroidchart)


    // PDF library
    implementation("com.itextpdf:itextpdf:5.5.13.3")
// If you use MPAndroidChart for charting (optional)
// implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    // WorkManager (background scheduling)
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    // SwipeRefreshLayout for pull-to-refresh UI
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging:23.2.0")
}