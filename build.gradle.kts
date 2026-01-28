// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false  // Add this line
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

val isWindows = System.getProperty("os.name").lowercase().contains("windows")

fun commandExists(command: String): Boolean {
    return try {
        val probe = if (isWindows) listOf("where", command) else listOf("which", command)
        val proc = ProcessBuilder(probe).redirectErrorStream(true).start()
        proc.waitFor() == 0
    } catch (e: Exception) {
        false
    }
}

tasks.register<Exec>("enforceRules") {
    group = "verification"
    description = "Enforce architecture/coding rules via scripts/ci/enforce_rules.ps1."
    val shell = when {
        commandExists("pwsh") -> "pwsh"
        isWindows && commandExists("powershell") -> "powershell"
        else -> "pwsh"
    }
    commandLine(shell, "-ExecutionPolicy", "Bypass", "-File", "scripts/ci/enforce_rules.ps1")
}
