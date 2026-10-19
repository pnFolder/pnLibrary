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
        url.set("https://github.com/${rootProject.property("pnlibrary.repository")}")

        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                url.set(providers.gradleProperty("POM_LICENSE_URL"))
                distribution.set(providers.gradleProperty("POM_LICENSE_DIST").orElse("repo"))
            }
        }
        developers {
            rootProject.property("pnlibrary.authors").toString().split(",").map(String::trim).forEach { author ->
                developer { id.set(author); name.set(author) }
            }
        }
        scm {
            url.set("https://github.com/${rootProject.property("pnlibrary.repository")}")
            connection.set("scm:git:git://github.com/${rootProject.property("pnlibrary.repository")}.git")
            developerConnection.set("scm:git:ssh://git@github.com/${rootProject.property("pnlibrary.repository")}.git")
        }
    }
}

base { archivesName = "pnLibrary-bukkit-api" }

dependencies {
    api(project(":modules:common"))
    api(project(":modules:api"))
    api(libs.kotlin.stdlib)
    compileOnly(libs.spigot.api.v18)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.spigot.api.v18)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
    withJavadocJar()
}
