// pnlibrary-core — no server-platform classes, JVM 8 bytecode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-core" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    api(project(":pnlibrary-api"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.gson)
    implementation(libs.snakeyaml)
    implementation(project(":pnlibrary-bstats-base"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly(libs.junit.launcher)
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
        artifactId = "pnlibrary-core",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary Core")
        description.set("Core implementation shared by pnLibrary platform modules.")
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
