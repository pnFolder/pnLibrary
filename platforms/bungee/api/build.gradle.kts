import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-bungee-api" }

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_1_8 }
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    api(project(":modules:api"))
    api(libs.kotlin.stdlib)
    compileOnly(libs.bungeecord.api)
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
