plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dagger.hilt)
    id("xcpro.secret-properties")
}

val xcproSecrets = extensions.getByType<com.example.xcpro.buildlogic.SecretPropertiesExtension>()

android {
    namespace = "com.trust3.xcpro.weglide"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "WEGLIDE_AUTHORIZATION_ENDPOINT",
            xcproSecrets.readBuildConfigString("WEGLIDE_AUTHORIZATION_ENDPOINT")
        )
        buildConfigField(
            "String",
            "WEGLIDE_TOKEN_ENDPOINT",
            xcproSecrets.readBuildConfigString("WEGLIDE_TOKEN_ENDPOINT")
        )
        buildConfigField(
            "String",
            "WEGLIDE_CLIENT_ID",
            xcproSecrets.readBuildConfigString("WEGLIDE_CLIENT_ID")
        )
        buildConfigField(
            "String",
            "WEGLIDE_REDIRECT_URI",
            xcproSecrets.asBuildConfigString(
                xcproSecrets.read("WEGLIDE_REDIRECT_URI").ifBlank { "xcpro://weglide-auth/callback" }
            )
        )
        buildConfigField(
            "String",
            "WEGLIDE_SCOPE",
            xcproSecrets.readBuildConfigString("WEGLIDE_SCOPE")
        )
        buildConfigField(
            "String",
            "WEGLIDE_USERINFO_ENDPOINT",
            xcproSecrets.readBuildConfigString("WEGLIDE_USERINFO_ENDPOINT")
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
