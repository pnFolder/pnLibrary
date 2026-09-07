// pnlibrary-bukkit — Bukkit/Spigot/Paper/Leaf/Purpur adapter, JVM 8 bytecode
// Folia is detected at runtime via reflection; no compile-time Folia API needed.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("bukkit") {
            from(components["java"])
            artifactId = "pnlibrary-bukkit"
        }
    }
}

base { archivesName = "pnLibrary-bukkit" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    api(project(":pnlibrary-core"))
    implementation(libs.kotlin.stdlib)
    implementation(project(":pnlibrary-bstats-base"))

    // 1.8.8 API covers the minimal surface we use; Paper 1.16.5 for compilation is fine
    compileOnly(libs.spigot.api.v18)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spigot.api.v18)
    testRuntimeOnly(libs.junit.launcher)
}

val resourceVersion = version.toString()
tasks.named<ProcessResources>("processResources") {
    inputs.property("version", resourceVersion)
    filesMatching("plugin.yml") {
        expand(mapOf("version" to resourceVersion))
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
