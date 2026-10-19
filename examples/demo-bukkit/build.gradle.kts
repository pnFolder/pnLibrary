// Production-style example plugin used to exercise pnLibrary visually on Bukkit/Paper.
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.jar.JarFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    `java-library`
}

base { archivesName = "pnLibrary-demo-bukkit" }

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
    relocate("org.pnfolder.pnlibrary.localization", "org.pnfolder.pnlibrary.demo.libs.localization")
    relocate("com.google.gson", "org.pnfolder.pnlibrary.demo.libs.gson")
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
                "org/pnfolder/pnlibrary/demo/DemoPlugin.class",
                "org/pnfolder/pnlibrary/demo/libs/localization/MinecraftLocalization.class",
                "org/pnfolder/pnlibrary/demo/libs/gson/Gson.class",
            )) check(jar.getJarEntry(entry) != null) { "Demo JAR is missing $entry" }
            for (entry in listOf(
                "org/pnfolder/pnlibrary/api/runtime/PnLibrary.class",
                "org/pnfolder/pnlibrary/core/runtime/PnLibraryImpl.class",
                "org/pnfolder/pnlibrary/localization/MinecraftLocalization.class",
                "com/google/gson/Gson.class",
                "kotlin/Unit.class",
            )) check(jar.getJarEntry(entry) == null) { "Demo JAR contains an unisolated dependency: $entry" }
        }
    }
}
tasks.named("check") { dependsOn(verifyDemoJar) }

val resourceVersion = project.version.toString()
tasks.named<ProcessResources>("processResources") {
    inputs.property("version", resourceVersion)
    filesMatching("plugin.yml") { expand(mapOf("version" to resourceVersion)) }
}
