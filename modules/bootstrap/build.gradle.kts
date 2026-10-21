plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-bootstrap" }

dependencies {
    api(project(":modules:console"))
    implementation(libs.gson)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
}

// The support link comes from gradle.properties, like PnLibraryIdentity, so it is written in one place only.
val defaultsDir = layout.buildDirectory.dir("generated/bootstrap/java")
val generateDefaults = tasks.register("generateBootstrapDefaults") {
    val supportUrl = rootProject.property("pnlibrary.support-url").toString()
    inputs.property("supportUrl", supportUrl)
    outputs.dir(defaultsDir)
    doLast {
        val target = defaultsDir.get().file("org/pnfolder/pnlibrary/bootstrap/BootstrapDefaults.java").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            |package org.pnfolder.pnlibrary.bootstrap;
            |
            |/** Generated from gradle.properties. Change the pnlibrary.* keys there, not this file. */
            |final class BootstrapDefaults {
            |    static final String SUPPORT_URL = "$supportUrl";
            |
            |    private BootstrapDefaults() {}
            |}
            |""".trimMargin(),
        )
    }
}
sourceSets.named("main") { java.srcDir(defaultsDir) }
tasks.named("compileJava") { dependsOn(generateDefaults) }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn(generateDefaults) }

tasks.withType<Test>().configureEach { useJUnitPlatform() }

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("io.github.pnfolder", "pnlibrary-bootstrap", project.version.toString())
    pom {
        name.set("pnLibrary Bootstrap")
        description.set("Platform-neutral core of the embeddable verified pnLibrary installer.")
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
