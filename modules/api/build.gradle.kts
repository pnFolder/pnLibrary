// pnlibrary-api — zero server-platform dependencies, JVM 8 bytecode

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "pnLibrary-api" }

// Product identity generated from gradle.properties (pnlibrary.* keys), so the name and author
// live in one file. The generated source is never edited by hand.
val identityDir = layout.buildDirectory.dir("generated/identity/kotlin")
val generateIdentity by tasks.registering {
    val productId = rootProject.property("pnlibrary.product-id").toString()
    val productName = rootProject.property("pnlibrary.name").toString()
    val authors = rootProject.property("pnlibrary.authors").toString()
        .split(",").map(String::trim).filter(String::isNotEmpty)
    val productVersion = rootProject.version.toString()
    val organization = rootProject.property("pnlibrary.organization").toString()
    val description = rootProject.property("pnlibrary.description").toString()
    val repository = rootProject.property("pnlibrary.repository").toString()
    val supportUrl = rootProject.property("pnlibrary.support-url").toString()
    inputs.property("organization", organization)
    inputs.property("description", description)
    inputs.property("repository", repository)
    inputs.property("supportUrl", supportUrl)
    inputs.property("productId", productId)
    inputs.property("productName", productName)
    inputs.property("authors", authors)
    inputs.property("version", productVersion)
    outputs.dir(identityDir)
    doLast {
        identityDir.get().asFile.deleteRecursively()
        val target = identityDir.get().file("org/pnfolder/pnlibrary/api/runtime/PnLibraryIdentity.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            |package org.pnfolder.pnlibrary.api.runtime
            |
            |/** Generated from gradle.properties. Change the pnlibrary.* keys there, not this file. */
            |object PnLibraryIdentity {
            |    const val PRODUCT_ID = "$productId"
            |    const val NAME = "$productName"
            |    const val ORGANIZATION = "$organization"
            |    const val DESCRIPTION = "$description"
            |    const val REPOSITORY = "$repository"
            |    const val REPOSITORY_URL = "https://github.com/$repository"
            |    const val SUPPORT_URL = "$supportUrl"
            |    /** Single display string for platforms that accept one author. */
            |    const val AUTHOR = "${authors.joinToString(", ")}"
            |    /** Every author, in declaration order, for platforms that accept a list. */
            |    val AUTHORS: List<String> = listOf(${authors.joinToString(", ") { "\"$it\"" }})
            |    const val VERSION = "$productVersion"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(identityDir)
}
tasks.named("compileKotlin") { dependsOn(generateIdentity) }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn(generateIdentity) }

dependencies {
    api(project(":modules:common"))
    api(project(":modules:features:config-api"))
    // Pure stdlib — no server-platform compile deps
    api(libs.kotlin.stdlib)
    api(libs.adventure.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// Public developer dependency. Runtime/platform JARs continue to be distributed
// through GitHub Releases and are intentionally not published to Maven Central.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.pnfolder",
        artifactId = "pnlibrary-api",
        version = project.version.toString()
    )

    pom {
        name.set("pnLibrary API")
        description.set("Public API for integrating Minecraft plugins with pnLibrary.")
        inceptionYear.set("2026")
        url.set("https://github.com/${rootProject.property("pnlibrary.repository")}")

        licenses {
            license {
                // Maven Central requires explicit license metadata. These values are
                // supplied as Gradle properties/CI variables so no license is silently
                // chosen on behalf of the project owner.
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
