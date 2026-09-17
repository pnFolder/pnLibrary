import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-velocity-api" }

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.withType<JavaCompile>().configureEach { options.release = 17 }

dependencies {
    api(project(":pnlibrary-api"))
    api(libs.kotlin.stdlib)
    compileOnly(libs.velocity.api)
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
