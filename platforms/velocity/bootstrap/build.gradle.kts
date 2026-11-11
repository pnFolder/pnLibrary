plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-bootstrap-velocity" }

dependencies {
    api(project(":modules:bootstrap"))
    compileOnly(libs.velocity.api)
}

java {
    withSourcesJar()
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-bootstrap-velocity", project.version.toString())
    pom {
        name.set("pnLibrary Velocity Bootstrap")
        description.set("Embeddable verified pnLibrary installer for Velocity plugins.")
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
