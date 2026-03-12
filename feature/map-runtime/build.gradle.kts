plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dagger.hilt)
}

android {
    namespace = "com.example.xcpro.map.runtime"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":dfcards-library"))
    implementation(project(":core:common"))
    implementation(project(":core:time"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:forecast"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:traffic"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.maplibre.android)
    implementation(libs.hilt.android)

    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
