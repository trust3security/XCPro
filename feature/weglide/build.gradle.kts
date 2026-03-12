plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dagger.hilt)
}

import java.util.Properties

fun readSecretProperty(name: String): String {
    val gradleValue = providers.gradleProperty(name).orNull?.trim().orEmpty()
    if (gradleValue.isNotEmpty()) return gradleValue
    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }
    return localProperties.getProperty(name)?.trim().orEmpty()
}

android {
    namespace = "com.example.xcpro.weglide"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "WEGLIDE_AUTHORIZATION_ENDPOINT",
            "\"${readSecretProperty("WEGLIDE_AUTHORIZATION_ENDPOINT")}\""
        )
        buildConfigField(
            "String",
            "WEGLIDE_TOKEN_ENDPOINT",
            "\"${readSecretProperty("WEGLIDE_TOKEN_ENDPOINT")}\""
        )
        buildConfigField(
            "String",
            "WEGLIDE_CLIENT_ID",
            "\"${readSecretProperty("WEGLIDE_CLIENT_ID")}\""
        )
        buildConfigField(
            "String",
            "WEGLIDE_REDIRECT_URI",
            "\"${readSecretProperty("WEGLIDE_REDIRECT_URI").ifBlank { "xcpro://weglide-auth/callback" }}\""
        )
        buildConfigField(
            "String",
            "WEGLIDE_SCOPE",
            "\"${readSecretProperty("WEGLIDE_SCOPE")}\""
        )
        buildConfigField(
            "String",
            "WEGLIDE_USERINFO_ENDPOINT",
            "\"${readSecretProperty("WEGLIDE_USERINFO_ENDPOINT")}\""
        )
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
    implementation(project(":core:common"))
    implementation(project(":core:time"))
    implementation(project(":feature:profile"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.hilt.android)

    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
