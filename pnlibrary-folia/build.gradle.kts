// pnlibrary-folia — Folia-specific scheduler integration
// Folia requires Paper ≥ 1.19.4 on Java 17, so this module targets JVM 17.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-folia" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach { options.release = 17 }

dependencies {
    api(project(":pnlibrary-bukkit"))
    implementation(libs.kotlin.stdlib)
    // Paper 1.21 is the reference for Folia API (io.papermc.paper.threadedregions)
    compileOnly(libs.paper.api.v1204)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.paper.api.v1204)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
