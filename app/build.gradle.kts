plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dagger.hilt)
    id("xcpro.secret-properties")
}

val xcproSecrets = extensions.getByType<com.example.xcpro.buildlogic.SecretPropertiesExtension>()

android {
    namespace = "com.trust3.xcpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trust3.xcpro"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Expose the MapLibre token via BuildConfig; supply through gradle.properties/local.properties
        val mapLibreKey: String = providers.gradleProperty("MAPLIBRE_API_KEY").orElse("").get()
        val openSkyClientId = xcproSecrets.read("OPENSKY_CLIENT_ID")
        val openSkyClientSecret = xcproSecrets.read("OPENSKY_CLIENT_SECRET")
        buildConfigField("String", "MAPLIBRE_API_KEY", "\"$mapLibreKey\"")
        buildConfigField("String", "OPENSKY_CLIENT_ID", xcproSecrets.asBuildConfigString(openSkyClientId))
        buildConfigField("String", "OPENSKY_CLIENT_SECRET", xcproSecrets.asBuildConfigString(openSkyClientSecret))

    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            // AI-NOTE: Use a distinct package for debug to avoid uninstalling the release app (and wiping user data).
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
        compose = true
    }
    lint {
        // Work around a lint FIR crash in androidTest sources (Kotlin analysis bug).
        checkTestSources = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":dfcards-library"))
    implementation(project(":core:common"))
    implementation(project(":core:geometry"))
    implementation(project(":core:time"))
    implementation(project(":core:ui"))
    implementation(project(":feature:forecast"))
    implementation(project(":feature:flight-runtime"))
    implementation(project(":feature:igc"))
    implementation(project(":feature:livefollow"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:map"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:traffic"))
    implementation(project(":feature:variometer"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:weglide"))

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.core.splashscreen)
    
    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.constraintlayout.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.maplibre.android)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
