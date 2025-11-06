# Version Catalog for XC Pro — Gradle (Kotlin DSL)
# Usage example in build.gradle.kts:
#   implementation(libs.androidx.core)
#   implementation(platform(libs.compose.bom))
#   implementation(libs.androidx.compose.ui)
#   ksp(libs.hilt.compiler)
#   testImplementation(libs.test.junit)

[versions]
# Align these with latest stable in your environment before first build.
agp = "8.7.2"
kotlin = "2.0.21"
jvmTarget = "17"
compileSdk = "35"
minSdk = "26"

composeBom = "2025.01.00"
activityCompose = "1.9.3"
lifecycle = "2.8.6"
coroutines = "1.9.0"
hilt = "2.52"
accompanist = "0.36.0"
navigation = "2.8.3"
datastore = "1.1.1"
playServicesLocation = "21.3.0"

ktlint = "0.51.0"
detekt = "1.23.6"

junit = "4.13.2"
androidxJunit = "1.2.1"
espresso = "3.6.1"
mockk = "1.13.12"
turbine = "1.1.0"
robolectric = "4.13.1"

[libraries]
# Kotlin/Coroutines
kotlin.stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib" }
coroutines.core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines.android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines.test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

# AndroidX Core & Lifecycle
androidx.core = { module = "androidx.core:core-ktx", version = "1.15.0" }
androidx.lifecycle.runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx.lifecycle.viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-ktx", version.ref = "lifecycle" }

# Compose (BOM)
compose.bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx.compose.ui = { module = "androidx.compose.ui:ui" }
androidx.compose.ui.tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx.compose.ui.tooling.preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx.compose.material3 = { module = "androidx.compose.material3:material3" }
androidx.activity.compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx.navigation.compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }

# Permissions / utilities
accompanist.permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }

# DI (Hilt)
hilt.android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt.compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }

# DataStore
androidx.datastore.preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }

# Location (GNSS)
play.services.location = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }

# Testing
test.junit = { module = "junit:junit", version.ref = "junit" }
androidx.test.junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
androidx.test.espresso = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
compose.ui.test = { module = "androidx.compose.ui:ui-test-junit4" }
compose.ui.manifest = { module = "androidx.compose.ui:ui-test-manifest" }

[bundles]
compose = [
  "androidx.compose.ui",
  "androidx.compose.ui.tooling.preview",
  "androidx.compose.material3",
  "androidx.activity.compose"
]
coroutines = [
  "coroutines.core",
  "coroutines.android"
]

[plugins]
android.application = { id = "com.android.application", version.ref = "agp" }
android.library = { id = "com.android.library", version.ref = "agp" }
kotlin.android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }

# Project-wide settings that you may reference in Gradle scripts
[versions.project]
minSdk = "26"
compileSdk = "35"
targetSdk = "35"
jvmTarget = "17"

