// pnlibrary-velocity — Velocity 3.x adapter
// Velocity 3 requires Java 17+; that is a platform requirement, not a preference.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-velocity" }

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
    api(project(":pnlibrary-core"))
    implementation(libs.kotlin.stdlib)
    implementation(project(":pnlibrary-bstats-base"))
    compileOnly(libs.velocity.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
