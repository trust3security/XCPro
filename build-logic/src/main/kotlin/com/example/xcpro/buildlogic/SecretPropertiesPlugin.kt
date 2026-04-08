package com.example.xcpro.buildlogic

import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.Project

open class SecretPropertiesExtension internal constructor(
    private val project: Project
) {
    private val localProperties: Properties by lazy {
        Properties().apply {
            val localPropertiesFile = project.rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use(::load)
            }
        }
    }

    fun read(name: String): String {
        val gradleValue = project.providers.gradleProperty(name).orNull?.trim().orEmpty()
        if (gradleValue.isNotEmpty()) return gradleValue
        return localProperties.getProperty(name)?.trim().orEmpty()
    }

    fun asBuildConfigString(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    fun readBuildConfigString(name: String): String = asBuildConfigString(read(name))
}

class SecretPropertiesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add("xcproSecrets", SecretPropertiesExtension(project))
    }
}
