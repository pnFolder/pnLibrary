// pnlibrary-api — zero server-platform dependencies, JVM 8 bytecode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
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
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Public developer dependency. Runtime/platform JARs continue to be distributed
// through GitHub Releases and are intentionally not published to Maven Central.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.pnfolder",
        artifactId = "pnlibrary-api",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary API")
        description.set("Public API for integrating Minecraft plugins with pnLibrary.")
        inceptionYear.set("2026")
        url.set("https://github.com/pnFolder/pnLibrary")

        licenses {
            license {
                // Maven Central requires explicit license metadata. These values are
                // supplied as Gradle properties/CI variables so no license is silently
                // chosen on behalf of the project owner.
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
