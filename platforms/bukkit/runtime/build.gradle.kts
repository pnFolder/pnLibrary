// pnlibrary-bukkit — Bukkit/Spigot/Paper/Leaf/Purpur adapter, JVM 8 bytecode
// Folia is detected at runtime via reflection; no compile-time Folia API needed.

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-bukkit" }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    implementation(project(":modules:core"))
    implementation(project(":modules:features:currency-api"))
    implementation(project(":platforms:bukkit:api"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.faststats.bukkit.j8)
    api(project(":modules:common"))
    api(project(":modules:design:console"))
    implementation(libs.adventure.legacy)
    implementation(libs.adventure.platform.bukkit)

    // 1.8.8 API covers the minimal surface we use; Paper 1.16.5 for compilation is fine
    compileOnly(libs.spigot.api.v18)
    compileOnly(libs.placeholderapi)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.spigot.api.v18)
    testRuntimeOnly(libs.junit.launcher)
}

val resourceVersion = version.toString()
val identityProperties = mapOf(
    "version" to resourceVersion,
    "name" to rootProject.property("pnlibrary.name").toString(),
    "authors" to rootProject.property("pnlibrary.authors").toString()
        .split(",").map(String::trim).filter(String::isNotEmpty)
        .joinToString(prefix = "[", postfix = "]") { "'$it'" },
    "author" to rootProject.property("pnlibrary.authors").toString().split(",").first().trim(),
    "description" to rootProject.property("pnlibrary.description").toString(),
    "website" to "https://github.com/" + rootProject.property("pnlibrary.repository"),
)
tasks.named<ProcessResources>("processResources") {
    inputs.properties(identityProperties)
    filesMatching("plugin.yml") {
        expand(identityProperties)
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.pnfolder",
        artifactId = "pnlibrary-bukkit",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary Bukkit")
        description.set("Bukkit, Spigot, Paper, Leaf and Purpur adapter for pnLibrary.")
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
