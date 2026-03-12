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

val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val pythonCommand = providers.environmentVariable("PYTHON").orNull ?: "python"
val configuredMaxParallelForks = providers.gradleProperty("xcpro.test.maxParallelForks")
    .orNull
    ?.toIntOrNull()
    ?.coerceAtLeast(1)
    ?: providers.environmentVariable("XC_TEST_PARALLEL_FORKS")
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
val flakyRobolectricAllowlistProvider = providers.provider {
    rootProject
        .file("config/test/flaky-robolectric-allowlist.txt")
        .takeIf { it.exists() }
        ?.readLines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?: emptyList()
}

fun Exec.configurePowerShellScript(scriptPath: String, vararg scriptArgs: String) {
    workingDir = rootDir
    if (isWindows) {
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", scriptPath, *scriptArgs)
    } else {
        commandLine("pwsh", "-File", scriptPath, *scriptArgs)
    }
}

fun Exec.configurePythonScript(scriptPath: String, vararg scriptArgs: String) {
    workingDir = rootDir
    commandLine(pythonCommand, scriptPath, *scriptArgs)
}

subprojects {
    tasks.withType<Test>().configureEach {
        maxParallelForks = configuredMaxParallelForks
        // Keep timeout policy as a per-test signal consumed by test infrastructure.
        // Task-level Gradle timeouts can terminate healthy full-suite runs.
        systemProperty("xcpro.test.timeout.seconds", configuredTestTimeoutSeconds.toString())
        if (perfEvidenceEnabled != null) {
            systemProperty("xcpro.enablePerfEvidence", perfEvidenceEnabled)
        }
    }

    if (isCi) {
        val flakyRobolectricAllowlist = flakyRobolectricAllowlistProvider.get()
        if (flakyRobolectricAllowlist.isNotEmpty()) {
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
}

tasks.register<Exec>("archGate") {
    group = "verification"
    description = "Enforce architecture invariants via scripts/arch_gate.py."
    configurePythonScript("scripts/arch_gate.py")
}

tasks.register<Exec>("enforceArchitectureRepoRules") {
    group = "verification"
    description = "Enforce the architecture-focused subset of scripts/ci/enforce_rules.ps1."
    configurePowerShellScript("scripts/ci/enforce_rules.ps1", "-RuleSet", "ArchitectureFast")
}

tasks.named("enforceArchitectureRepoRules") {
    mustRunAfter("archGate")
}

tasks.register("enforceArchitectureFast") {
    group = "verification"
    description = "Run arch_gate.py plus the architecture-focused subset of enforce_rules.ps1."
    dependsOn("archGate", "enforceArchitectureRepoRules")
}

tasks.register<Exec>("enforceRepositoryRules") {
    group = "verification"
    description = "Enforce the full repository rule set via scripts/ci/enforce_rules.ps1."
    configurePowerShellScript("scripts/ci/enforce_rules.ps1", "-RuleSet", "RepositoryFull")
}

tasks.named("enforceRepositoryRules") {
    mustRunAfter("archGate")
}

tasks.register("enforceRules") {
    group = "verification"
    description = "Run arch_gate.py plus the full repository rule set."
    dependsOn("archGate", "enforceRepositoryRules")
}
