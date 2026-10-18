plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-velocity-api", project.version.toString())
    pom {
        name.set("pnLibrary Velocity API")
        description.set("Public Velocity platform contract for pnLibrary integrations.")
        inceptionYear.set("2026")
        url.set("https://github.com/pnFolder/pnLibrary")
        licenses { license { name.set(providers.gradleProperty("POM_LICENSE_NAME")); url.set(providers.gradleProperty("POM_LICENSE_URL")); distribution.set(providers.gradleProperty("POM_LICENSE_DIST").orElse("repo")) } }
        developers { developer { id.set("pnFolder"); name.set("pnFolder"); url.set("https://github.com/pnFolder") } }
        scm { url.set("https://github.com/pnFolder/pnLibrary"); connection.set("scm:git:git://github.com/pnFolder/pnLibrary.git"); developerConnection.set("scm:git:ssh://git@github.com/pnFolder/pnLibrary.git") }
    }
}

base { archivesName = "pnLibrary-velocity-api" }

dependencies {
    api(project(":modules:api"))
    api(libs.kotlin.stdlib)
    compileOnly(libs.velocity.api)
}

java {
    withSourcesJar()
    withJavadocJar()
}
