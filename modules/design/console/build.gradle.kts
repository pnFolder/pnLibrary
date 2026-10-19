plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-console-style" }

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-console-style", project.version.toString())
    pom {
        name.set("pnLibrary Console Style")
        description.set("Dependency-free styled console cards for pnFolder plugins.")
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
        scm { url.set("https://github.com/${rootProject.property("pnlibrary.repository")}") }
    }
}
