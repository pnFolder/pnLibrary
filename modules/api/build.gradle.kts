// pnlibrary-api — zero server-platform dependencies, JVM 8 bytecode

plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-api" }

// Product identity generated from gradle.properties (pnlibrary.* keys), so the name and author
// live in one file. The generated source is never edited by hand.
val identityDir = layout.buildDirectory.dir("generated/identity/java")
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
        fun literal(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        identityDir.get().asFile.deleteRecursively()
        val target = identityDir.get().file("org/pnfolder/pnlibrary/api/runtime/PnLibraryIdentity.java").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            |package org.pnfolder.pnlibrary.api.runtime;
            |
            |import java.util.Arrays;
            |import java.util.Collections;
            |import java.util.List;
            |
            |/** Generated from gradle.properties. Change the pnlibrary.* keys there, not this file. */
            |public final class PnLibraryIdentity {
            |    public static final String PRODUCT_ID = ${literal(productId)};
            |    public static final String NAME = ${literal(productName)};
            |    public static final String ORGANIZATION = ${literal(organization)};
            |    public static final String DESCRIPTION = ${literal(description)};
            |    public static final String REPOSITORY = ${literal(repository)};
            |    public static final String REPOSITORY_URL = runtimeValue(${literal("https://github.com/$repository")});
            |    /**
            |     * Where to get help. Not a constant on purpose: a constant is copied into the bytecode of every plugin built against this API,
            |     * so a new link would reach only rebuilt plugins. A plain field is read from the running library instead.
            |     */
            |    public static final String SUPPORT_URL = runtimeValue(${literal(supportUrl)});
            |    /** Single display string for platforms that accept one author. */
            |    public static final String AUTHOR = ${literal(authors.joinToString(", "))};
            |    /** Every author, in declaration order, for platforms that accept a list. */
            |    public static final List<String> AUTHORS = Collections.unmodifiableList(Arrays.asList(${authors.joinToString(", ") { literal(it) }}));
            |    public static final String VERSION = ${literal(productVersion)};
            |
            |    private PnLibraryIdentity() {
            |    }
            |
            |    private static String runtimeValue(String value) {
            |        return value;
            |    }
            |}
            |""".trimMargin(),
        )
    }
}

sourceSets.named("main") {
    java.srcDir(identityDir)
}
tasks.named("compileJava") { dependsOn(generateIdentity) }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn(generateIdentity) }

dependencies {
    api(project(":modules:common"))
    api(project(":modules:features:config-api"))
    api(project(":modules:features:localization-api"))
    api(libs.adventure.api)
    api(libs.jetbrains.annotations)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// Public developer dependency. Runtime/platform JARs continue to be distributed
// through GitHub Releases and are intentionally not published to Maven Central.
mavenPublishing {
    publishToMavenCentral()
    // Without a key (a local build) the artifacts are published unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

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
