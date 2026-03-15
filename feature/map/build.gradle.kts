plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dagger.hilt)
}

import java.util.Properties

fun String.asBuildConfigString(): String {
    val escaped = this.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

val localProperties: Properties by lazy {
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }
}

fun readSecretProperty(name: String): String {
    val gradleValue = providers.gradleProperty(name).orNull?.trim().orEmpty()
    if (gradleValue.isNotEmpty()) return gradleValue
    return localProperties.getProperty(name)?.trim().orEmpty()
}

val openSkyClientId = readSecretProperty("OPENSKY_CLIENT_ID")
val openSkyClientSecret = readSecretProperty("OPENSKY_CLIENT_SECRET")
android {
    namespace = "com.example.xcpro.map"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "OPENSKY_CLIENT_ID", openSkyClientId.asBuildConfigString())
            buildConfigField(
                "String",
                "OPENSKY_CLIENT_SECRET",
                openSkyClientSecret.asBuildConfigString()
            )
        }
        release {
            buildConfigField("String", "OPENSKY_CLIENT_ID", openSkyClientId.asBuildConfigString())
            buildConfigField(
                "String",
                "OPENSKY_CLIENT_SECRET",
                openSkyClientSecret.asBuildConfigString()
            )
        }
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core:geometry"))
    implementation(project(":core:time"))
    implementation(project(":core:ui"))
    implementation(project(":feature:forecast"))
    implementation(project(":feature:flight-runtime"))
    implementation(project(":feature:igc"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:map-runtime"))
    implementation(project(":feature:traffic"))
    implementation(project(":feature:variometer"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:weglide"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.maplibre.android)
    implementation(libs.maplibre.scalebar)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(project(":dfcards-library"))
    testImplementation(project(":core:common"))
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.0")
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

val hasAndroidTests = providers.provider {
    file("src/androidTest/java").exists() || file("src/androidTest/kotlin").exists()
}

tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    onlyIf("Skip connected tests when no androidTest sources are present.") { hasAndroidTests.get() }
}
