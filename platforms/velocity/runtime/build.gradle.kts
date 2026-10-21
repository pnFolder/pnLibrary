// pnlibrary-velocity — Velocity 3.x adapter
// Velocity 3 requires Java 17+; that is a platform requirement, not a preference.

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "pnLibrary-velocity" }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    implementation(project(":modules:core"))
    implementation(project(":platforms:velocity:api"))
    implementation(libs.kotlin.stdlib)
    // The adapter is instantiated only after the factory checks for Java 21. The bridge is built for Java 21,
    // so this Java 17 module asks for that variant explicitly instead of failing Gradle's JVM-version matching.
    compileOnly(project(":platforms:velocity:faststats")) {
        attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21) }
    }
    compileOnly(libs.velocity.api)
    // Provided by the proxy; used only to observe plugin warnings and errors.
    compileOnly(libs.log4j.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.velocity.api)
    testImplementation(libs.log4j.core)
    testRuntimeOnly(libs.junit.launcher)
}

java {
    withSourcesJar()
}

// Velocity's @Plugin annotation takes compile-time literals only, so the descriptor class is generated
// from gradle.properties. Lifecycle logic stays in VelocityRuntime.
val descriptorDir = layout.buildDirectory.dir("generated/velocity/kotlin")
val descriptorResources = layout.buildDirectory.dir("generated/velocity/resources")
val generateVelocityDescriptor by tasks.registering {
    val props = rootProject
    val productId = props.property("pnlibrary.product-id").toString()
    val productName = props.property("pnlibrary.name").toString()
    val productVersion = props.version.toString()
    val description = props.property("pnlibrary.description").toString()
    val repositoryUrl = "https://github.com/" + props.property("pnlibrary.repository")
    val authors = props.property("pnlibrary.authors").toString()
        .split(",").map(String::trim).filter(String::isNotEmpty)
    inputs.property("productId", productId)
    inputs.property("productName", productName)
    inputs.property("productVersion", productVersion)
    inputs.property("description", description)
    inputs.property("repositoryUrl", repositoryUrl)
    inputs.property("authors", authors)
    outputs.dir(descriptorDir)
    outputs.dir(descriptorResources)
    doLast {
        descriptorDir.get().asFile.deleteRecursively()
        descriptorResources.get().asFile.deleteRecursively()
        val json = descriptorResources.get().file("velocity-plugin.json").asFile
        json.parentFile.mkdirs()
        json.writeText(
            """
            |{
            |  "id": "$productId",
            |  "name": "$productName",
            |  "version": "$productVersion",
            |  "description": "$description",
            |  "url": "$repositoryUrl",
            |  "authors": [${authors.joinToString(", ") { "\"$it\"" }}],
            |  "main": "org.pnfolder.pnlibrary.velocity.PnLibraryVelocityPlugin"
            |}
            |""".trimMargin(),
        )
        val target = descriptorDir.get().file("org/pnfolder/pnlibrary/velocity/PnLibraryVelocityPlugin.kt").asFile
        target.parentFile.mkdirs()
        val authorLiterals = authors.joinToString(", ") { "\"$it\"" }
        target.writeText(
            """
            |package org.pnfolder.pnlibrary.velocity
            |
            |import com.google.inject.Inject
            |import com.velocitypowered.api.event.Subscribe
            |import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
            |import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
            |import com.velocitypowered.api.plugin.Plugin
            |import com.velocitypowered.api.plugin.annotation.DataDirectory
            |import com.velocitypowered.api.proxy.ProxyServer
            |import org.bstats.velocity.Metrics
            |import org.slf4j.Logger
            |import java.nio.file.Path
            |
            |/** Generated from gradle.properties. Change the pnlibrary.* keys there, not this file. */
            |@Plugin(
            |    id = "$productId",
            |    name = "$productName",
            |    version = "$productVersion",
            |    description = "$description",
            |    url = "$repositoryUrl",
            |    authors = [$authorLiterals],
            |)
            |class PnLibraryVelocityPlugin @Inject constructor(
            |    server: ProxyServer,
            |    logger: Logger,
            |    metricsFactory: Metrics.Factory,
            |    @DataDirectory dataDirectory: Path,
            |) {
            |    private val runtime = VelocityRuntime(this, server, logger, metricsFactory, dataDirectory)
            |
            |    @Subscribe
            |    fun onInitialize(event: ProxyInitializeEvent) = runtime.onInitialize(event)
            |
            |    @Subscribe
            |    fun onShutdown(event: ProxyShutdownEvent) = runtime.onShutdown(event)
            |}
            |""".trimMargin(),
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(descriptorDir)
    resources.srcDir(descriptorResources)
}
tasks.named("compileKotlin") { dependsOn(generateVelocityDescriptor) }
tasks.named("processResources") { dependsOn(generateVelocityDescriptor) }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn(generateVelocityDescriptor) }
