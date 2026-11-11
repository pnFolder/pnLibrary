// pnlibrary-velocity — Velocity 3.x adapter
// Velocity 3 requires Java 17+; that is a platform requirement, not a preference.

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

base { archivesName = "${rootProject.property("pnlibrary.name")}-velocity" }

dependencies {
    implementation(project(":modules:api"))
    implementation(project(":modules:runtime-spi"))
    implementation(project(":modules:core"))
    implementation(project(":platforms:velocity:api"))
    implementation(libs.kotlin.stdlib)
    implementation(project(":platforms:loader"))
    implementation(libs.libby.velocity)
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
        val target = descriptorDir.get().file("org/pnfolder/pnlibrary/velocity/PnLibraryVelocityPlugin.java").asFile
        target.parentFile.mkdirs()
        val authorLiterals = authors.joinToString(", ") { "\"$it\"" }
        target.writeText(
            """
            |package org.pnfolder.pnlibrary.velocity;
            |
            |import com.google.inject.Inject;
            |import com.velocitypowered.api.event.Subscribe;
            |import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
            |import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
            |import com.velocitypowered.api.plugin.Plugin;
            |import com.velocitypowered.api.plugin.annotation.DataDirectory;
            |import com.velocitypowered.api.proxy.ProxyServer;
            |import java.lang.reflect.Constructor;
            |import java.lang.reflect.InvocationTargetException;
            |import java.lang.reflect.Method;
            |import java.nio.file.Path;
            |import net.byteflux.libby.VelocityLibraryManager;
            |import org.pnfolder.pnlibrary.loader.LibraryLoader;
            |import org.pnfolder.pnlibrary.loader.LibrarySettings;
            |import org.pnfolder.pnlibrary.loader.StartupFailure;
            |import org.pnfolder.pnlibrary.console.ConsoleTheme;
            |import net.kyori.adventure.text.Component;
            |import org.pnfolder.pnlibrary.loader.RuntimeLibraries;
            |import org.slf4j.Logger;
            |
            |/**
            | * Generated from gradle.properties. Change the pnlibrary.* keys there, not this file.
            | *
            | * <p>Plain Java on purpose: the light JAR has no Kotlin until {@link LibraryLoader} has added it,
            | * so the Kotlin runtime is created by reflection after that.
            | */
            |@Plugin(
            |    id = "\"$productId\"",
            |    name = "\"$productName\"",
            |    version = "\"$productVersion\"",
            |    description = "\"$description\"",
            |    url = "\"$repositoryUrl\"",
            |    authors = {$authorLiterals}
            |)
            |public final class PnLibraryVelocityPlugin {
            |    private static final String RUNTIME = "org.pnfolder.pnlibrary.velocity.VelocityRuntime";
            |
            |    private final ProxyServer server;
            |    private final Logger logger;
            |    private final Path dataDirectory;
            |    private Object runtime;
            |
            |    @Inject
            |    public PnLibraryVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
            |        this.server = server;
            |        this.logger = logger;
            |        this.dataDirectory = dataDirectory;
            |    }
            |
            |    @Subscribe
            |    public void onInitialize(ProxyInitializeEvent event) throws Exception {
            |        LibrarySettings settings = LibrarySettings.DEFAULT;
            |        try {
            |            ClassLoader loader = getClass().getClassLoader();
            |            LibraryLoader.load(new VelocityLibraryManager<>(logger, dataDirectory, server.getPluginManager(), this, settings.directoryFor(dataDirectory)), loader, settings, RuntimeLibraries.VELOCITY);
            |            Class<?> type = Class.forName(RUNTIME, true, loader);
            |            Constructor<?> constructor = type.getConstructors()[0];
            |            runtime = constructor.newInstance(this, server, logger, dataDirectory);
            |            invoke("onInitialize", event);
            |        } catch (Exception | LinkageError failure) {
            |            runtime = null;
            |            report(settings, failure);
            |        }
            |    }
            |
            |    private void report(LibrarySettings settings, Throwable failure) {
            |        try {
            |            StartupFailure.report(ConsoleTheme.plain(),
            |                line -> server.getConsoleCommandSource().sendMessage(Component.text(line)), settings,
            |                dataDirectory, failure);
            |        } catch (RuntimeException | LinkageError cardFailed) {
            |            logger.error("pnLibrary could not start: " + failure);
            |        }
            |    }
            |
            |    @Subscribe
            |    public void onShutdown(ProxyShutdownEvent event) throws Exception {
            |        if (runtime != null) {
            |            invoke("onShutdown", event);
            |        }
            |    }
            |
            |    private void invoke(String name, Object event) throws Exception {
            |        for (Method method : runtime.getClass().getMethods()) {
            |            if (method.getName().equals(name) && method.getParameterCount() == 1) {
            |                try {
            |                    method.invoke(runtime, event);
            |                } catch (InvocationTargetException failure) {
            |                    if (failure.getCause() instanceof Exception) throw (Exception) failure.getCause();
            |                    throw failure;
            |                }
            |                return;
            |            }
            |        }
            |        throw new NoSuchMethodException(name);
            |    }
            |}
            |""".trimMargin(),
        )
    }
}

kotlin.sourceSets.named("main") {
    resources.srcDir(descriptorResources)
}
sourceSets.named("main") { java.srcDir(descriptorDir) }
tasks.named("compileKotlin") { dependsOn(generateVelocityDescriptor) }
tasks.named("compileJava") { dependsOn(generateVelocityDescriptor) }
tasks.named("processResources") { dependsOn(generateVelocityDescriptor) }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn(generateVelocityDescriptor) }
