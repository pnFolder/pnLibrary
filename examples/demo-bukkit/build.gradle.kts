// Production-style example plugin used to exercise pnLibrary visually on Bukkit/Paper.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-demo-bukkit" }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }
tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    compileOnly(project(":modules:api"))
    compileOnly(project(":platforms:bukkit:api"))
    compileOnly(libs.spigot.api.v18)
    compileOnly(libs.kotlin.stdlib)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand(mapOf("version" to project.version)) }
}
