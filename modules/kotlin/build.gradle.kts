// pnlibrary-kotlin: optional Kotlin helpers over the Java API. Plugins written in Java do not need it, and it is not part
// of the runtime jars (kotlin-stdlib comes from the plugin that uses it).

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-kotlin" }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        // Extensions use only what the Java API already promises; the stdlib is the plugin's own.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
    explicitApi()
}

dependencies {
    api(project(":modules:api"))
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.launcher)
}

// Public developer dependency. Runtime/platform JARs continue to be distributed
// through GitHub Releases and are intentionally not published to Maven Central.
mavenPublishing {
    publishToMavenCentral()
    // Without a key (a local build) the artifacts are published unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    coordinates(
        groupId = "io.github.pnfolder",
        artifactId = "pnlibrary-kotlin",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary Kotlin")
        description.set("Kotlin extensions for the pnLibrary API: reified service and event calls, a codec builder and more.")
        inceptionYear.set("2026")
        url.set("https://github.com/${rootProject.property("pnlibrary.repository")}")

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
            rootProject.property("pnlibrary.authors").toString().split(",").map(String::trim).forEach { author ->
                developer {
                    id.set(author)
                    name.set(author)
                    url.set("https://github.com/" + rootProject.property("pnlibrary.organization"))
                }
            }
        }

        scm {
            url.set("https://github.com/${rootProject.property("pnlibrary.repository")}")
            connection.set("scm:git:git://github.com/${rootProject.property("pnlibrary.repository")}.git")
            developerConnection.set("scm:git:ssh://git@github.com/${rootProject.property("pnlibrary.repository")}.git")
        }
    }
}
