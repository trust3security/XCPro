plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        register("secretProperties") {
            id = "xcpro.secret-properties"
            implementationClass = "com.example.xcpro.buildlogic.SecretPropertiesPlugin"
        }
    }
}
