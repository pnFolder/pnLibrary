// pnlibrary-api — zero server-platform dependencies, JVM 8 bytecode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("api") {
            from(components["java"])
            artifactId = "pnlibrary-api"
        }
    }
}

base { archivesName = "pnLibrary-api" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    // Pure stdlib — no server-platform compile deps
    api(libs.kotlin.stdlib)
    api(libs.adventure.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
