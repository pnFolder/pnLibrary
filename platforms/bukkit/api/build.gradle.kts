import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.pnfolder",
        artifactId = "pnlibrary-bukkit-api",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary Bukkit API")
        description.set("Public Bukkit utilities and contracts for pnLibrary integrations.")
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

base { archivesName = "pnLibrary-bukkit-api" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    api(project(":modules:api"))
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
