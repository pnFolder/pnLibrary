// pnlibrary-core — no server-platform classes, JVM 8 bytecode

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}
base { archivesName = "${rootProject.property("pnlibrary.name")}-core" }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    api(project(":modules:updates"))
    implementation(project(":modules:features:currency-runtime"))
    implementation(project(":modules:features:config-runtime"))
    implementation(project(":modules:features:minecraft-localization-api"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.gson)
    implementation(libs.snakeyaml)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.legacy)
    implementation(libs.adventure.gson)
    implementation(libs.adventure.plain)
    compileOnly(libs.faststats.core.j8)
    implementation(project(":modules:console"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.launcher)
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
