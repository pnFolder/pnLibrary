// pnlibrary-bungee — BungeeCord / Waterfall adapter, JVM 8 bytecode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-bungee" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    implementation(project(":modules:core"))
    implementation(project(":platforms:bungee:api"))
    implementation(libs.kotlin.stdlib)
    implementation(project(":modules:internal:bstats"))
    implementation(libs.adventure.legacy)
    compileOnly(libs.bungeecord.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.bungeecord.api)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
