import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("bukkitApi") {
            from(components["java"])
            artifactId = "pnlibrary-bukkit-api"
        }
    }
}

base { archivesName = "pnLibrary-bukkit-api" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    api(project(":pnlibrary-api"))
    api(libs.kotlin.stdlib)
    compileOnly(libs.spigot.api.v18)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spigot.api.v18)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
