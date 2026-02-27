// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false  // Add this line
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.ksp) apply false
    id("org.gradle.test-retry") version "1.6.2" apply false
}

import org.gradle.api.tasks.testing.Test
import org.gradle.testretry.TestRetryTaskExtension
import java.time.Duration

val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val configuredMaxParallelForks = providers.gradleProperty("xcpro.test.maxParallelForks")
    .orNull
    ?.toIntOrNull()
    ?.coerceAtLeast(1)
    ?: 1
val configuredTestTimeoutSeconds = providers.gradleProperty("xcpro.test.timeout.seconds")
    .orNull
    ?.toLongOrNull()
    ?.coerceIn(10L, 120L)
    ?: 60L
val perfEvidenceEnabled = providers.gradleProperty("xcproEnablePerfEvidence").orNull
val isCi = providers.environmentVariable("CI")
    .orNull
    ?.equals("true", ignoreCase = true)
    ?: false
val flakyRobolectricAllowlist = rootProject
    .file("config/test/flaky-robolectric-allowlist.txt")
    .takeIf { it.exists() }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") }
    ?: emptyList()

subprojects {
    tasks.withType<Test>().configureEach {
        maxParallelForks = configuredMaxParallelForks
        timeout.set(Duration.ofSeconds(configuredTestTimeoutSeconds))
        systemProperty("xcpro.test.timeout.seconds", configuredTestTimeoutSeconds.toString())
        if (perfEvidenceEnabled != null) {
            systemProperty("xcpro.enablePerfEvidence", perfEvidenceEnabled)
        }
    }

    if (isCi && flakyRobolectricAllowlist.isNotEmpty()) {
        apply(plugin = "org.gradle.test-retry")
        tasks.withType<Test>().configureEach {
            extensions.configure(TestRetryTaskExtension::class.java) {
                maxRetries.set(1)
                maxFailures.set(20)
                failOnPassedAfterRetry.set(false)
                filter {
                    includeClasses.addAll(flakyRobolectricAllowlist)
                }
            }
        }
    }
}

tasks.register<Exec>("enforceRules") {
    group = "verification"
    description = "Enforce architecture/coding rules via scripts/ci/enforce_rules.ps1."
    if (isWindows) {
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/ci/enforce_rules.ps1")
    } else {
        commandLine("pwsh", "-File", "scripts/ci/enforce_rules.ps1")
    }
}
