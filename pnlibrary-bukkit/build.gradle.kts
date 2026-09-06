// pnlibrary-bukkit — Bukkit/Spigot/Paper/Leaf/Purpur adapter, JVM 8 bytecode
// Folia is detected at runtime via reflection; no compile-time Folia API needed.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
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

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version.toString())
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version.toString()))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.pnfolder",
        artifactId = "pnlibrary-bukkit",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary Bukkit")
        description.set("Bukkit, Spigot, Paper, Leaf and Purpur adapter for pnLibrary.")
        inceptionYear.set("2026")
        url.set("https://github.com/pnFolder/pnLibrary")

        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                url.set(providers.gradleProperty("POM_LICENSE_URL"))
                distribution.set(providers.gradleProperty("POM_LICENSE_DIST").orElse("repo"))
            }
        }

        developers {
            developer {
                id.set("pnFolder")
                name.set("pnFolder")
                url.set("https://github.com/pnFolder")
            }
        }

        scm {
            url.set("https://github.com/pnFolder/pnLibrary")
            connection.set("scm:git:git://github.com/pnFolder/pnLibrary.git")
            developerConnection.set("scm:git:ssh://git@github.com/pnFolder/pnLibrary.git")
        }
    }
}
