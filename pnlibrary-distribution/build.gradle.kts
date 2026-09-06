// pnlibrary-distribution — produces fat JARs for each platform with relocated Kotlin runtime
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.shadow)
    java
}

// This module produces artifacts only; it has no sources of its own.
sourceSets.main.configure { java.setSrcDirs(emptyList<File>()) }
tasks.named<Jar>("jar") { enabled = false }
tasks.named<ShadowJar>("shadowJar") { enabled = false }

val pnVer = project.version.toString()

// ── Relocation config shared by all shadow tasks ─────────────────────────────
fun ShadowJar.applyCommonConfig(suffix: String) {
    archiveClassifier = ""
    archiveVersion    = pnVer
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Relocate Kotlin stdlib & reflect so they don't clash with other plugins
    relocate("kotlin.",                 "ru.privatenull.pnlibrary.libs.kotlin.")
    relocate("kotlinx.",                "ru.privatenull.pnlibrary.libs.kotlinx.")
    relocate("org.intellij.",           "ru.privatenull.pnlibrary.libs.intellij.")
    relocate("org.jetbrains.",          "ru.privatenull.pnlibrary.libs.jetbrains.")
    relocate("com.google.gson",         "ru.privatenull.pnlibrary.libs.gson")
    relocate("org.yaml.snakeyaml",      "ru.privatenull.pnlibrary.libs.yaml")
    relocate("org.bstats",              "ru.privatenull.pnlibrary.libs.bstats")

    exclude("META-INF/versions/**")
    exclude("module-info.class")
    exclude("META-INF/*.SF")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.DSA")
    exclude("META-INF/INDEX.LIST")
    exclude("META-INF/*.kotlin_module")
    // Strip Kotlin source maps from the fat JAR
    exclude("**/*.kotlin_builtins")
}

// ── Bukkit / Spigot / Paper / Leaf / Purpur / Folia (universal) ──────────────
val bukkitRuntime: Configuration by configurations.creating
dependencies {
    bukkitRuntime(project(":pnlibrary-bukkit"))
    // Folia scheduler layer is included at runtime; consumers decide whether to activate it
}

tasks.register<ShadowJar>("shadowBukkit") {
    group = "distribution"
    description = "Fat JAR for Bukkit/Spigot/Paper/Folia with relocated Kotlin runtime"
    archiveBaseName = "pnLibrary-bukkit"
    configurations = listOf(bukkitRuntime)
    applyCommonConfig("bukkit")
    filesMatching("plugin.yml") { expand("version" to pnVer) }
}

// ── BungeeCord / Waterfall ────────────────────────────────────────────────────
val bungeeRuntime: Configuration by configurations.creating
dependencies {
    bungeeRuntime(project(":pnlibrary-bungee"))
}

tasks.register<ShadowJar>("shadowBungee") {
    group = "distribution"
    description = "Fat JAR for BungeeCord / Waterfall with relocated Kotlin runtime"
    archiveBaseName = "pnLibrary-bungee"
    configurations = listOf(bungeeRuntime)
    applyCommonConfig("bungee")
    filesMatching("bungee.yml") { expand("version" to pnVer) }
}

// ── Velocity ─────────────────────────────────────────────────────────────────
val velocityRuntime: Configuration by configurations.creating
dependencies {
    velocityRuntime(project(":pnlibrary-velocity"))
}

tasks.register<ShadowJar>("shadowVelocity") {
    group = "distribution"
    description = "Fat JAR for Velocity 3.x with relocated Kotlin runtime"
    archiveBaseName = "pnLibrary-velocity"
    configurations = listOf(velocityRuntime)
    applyCommonConfig("velocity")
    filesMatching("velocity-plugin.json") { expand("version" to pnVer) }
}

tasks.named("build") {
    dependsOn("shadowBukkit", "shadowBungee", "shadowVelocity")
}
