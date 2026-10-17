// Production-style example plugin used to exercise pnLibrary visually on Bukkit/Paper.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.JarFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    `java-library`
}

base { archivesName = "pnLibrary-demo-bukkit" }

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }
tasks.withType<JavaCompile>().configureEach { options.release = 8 }

dependencies {
    compileOnly(project(":modules:api"))
    compileOnly(project(":modules:features:currency-api"))
    compileOnly(project(":platforms:bukkit:api"))
    // Optional feature code belongs to this plugin; shared pnLibrary APIs stay compile-only.
    implementation(project(":modules:features:minecraft-localization")) { isTransitive = false }
    implementation(libs.gson)
    compileOnly(libs.spigot.api.v18)
    compileOnly(libs.kotlin.stdlib)
}

tasks.named<Jar>("jar") { archiveClassifier.set("plain") }
val demoJar = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    relocate("ru.privatenull.pnlibrary.localization", "ru.privatenull.pnlibrary.demo.libs.localization")
    relocate("com.google.gson", "ru.privatenull.pnlibrary.demo.libs.gson")
}
tasks.named("assemble") { dependsOn("shadowJar") }

val verifyDemoJar = tasks.register("verifyDemoJar") {
    group = "verification"
    description = "Checks that the demo bundles its optional feature while sharing the pnLibrary API"
    dependsOn(demoJar)
    inputs.file(demoJar.flatMap { it.archiveFile })
    doLast {
        JarFile(demoJar.get().archiveFile.get().asFile).use { jar ->
            for (entry in listOf(
                "plugin.yml",
                "ru/privatenull/pnlibrary/demo/DemoPlugin.class",
                "ru/privatenull/pnlibrary/demo/libs/localization/MinecraftLocalization.class",
                "ru/privatenull/pnlibrary/demo/libs/gson/Gson.class",
            )) check(jar.getJarEntry(entry) != null) { "Demo JAR is missing $entry" }
            for (entry in listOf(
                "ru/privatenull/pnlibrary/api/runtime/PnLibrary.class",
                "ru/privatenull/pnlibrary/core/runtime/PnLibraryImpl.class",
                "ru/privatenull/pnlibrary/localization/MinecraftLocalization.class",
                "com/google/gson/Gson.class",
                "kotlin/Unit.class",
            )) check(jar.getJarEntry(entry) == null) { "Demo JAR contains an unisolated dependency: $entry" }
        }
    }
}
tasks.named("check") { dependsOn(verifyDemoJar) }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val resourceVersion = project.version.toString()
tasks.named<ProcessResources>("processResources") {
    inputs.property("version", resourceVersion)
    filesMatching("plugin.yml") { expand(mapOf("version" to resourceVersion)) }
}
