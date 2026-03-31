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
    namespace = "com.example.xcpro.livefollow"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
        buildConfigField(
            "String",
            "XCPRO_PRIVATE_FOLLOW_DEV_BEARER_TOKEN",
            "\"${readSecretProperty("XCPRO_PRIVATE_FOLLOW_DEV_BEARER_TOKEN")}\""
        )
        buildConfigField(
            "String",
            "XCPRO_GOOGLE_SERVER_CLIENT_ID",
            "\"${readSecretProperty("XCPRO_GOOGLE_SERVER_CLIENT_ID")}\""
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:time"))
    implementation(project(":dfcards-library"))
    implementation(project(":feature:flight-runtime"))
    implementation(project(":feature:traffic"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.googleid)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.0")
    debugImplementation(libs.androidx.ui.tooling)
}
