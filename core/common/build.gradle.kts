plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.xcpro.core.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":dfcards-library"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core.android)
    implementation("javax.inject:javax.inject:1")
}
