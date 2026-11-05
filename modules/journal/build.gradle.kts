plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-journal" }

dependencies {
    api(project(":modules:api"))
    implementation(project(":modules:common"))
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.legacy)
    implementation(libs.adventure.gson)
    implementation(libs.adventure.plain)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.launcher)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-journal", project.version.toString())
    pom {
        name.set("pnLibrary journal")
        description.set("Event journal and its chat pages for pnLibrary.")
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
