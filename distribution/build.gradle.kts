// pnlibrary-distribution — produces fat JARs for each platform with relocated Kotlin runtime
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.security.MessageDigest
import java.util.jar.JarFile

evaluationDependsOn(":modules:api")
evaluationDependsOn(":modules:common")
evaluationDependsOn(":modules:features:minecraft-localization")
evaluationDependsOn(":platforms:bukkit:api")
evaluationDependsOn(":platforms:bungee:api")
evaluationDependsOn(":platforms:velocity:api")

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
fun ShadowJar.applyCommonConfig() {
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
    bukkitRuntime(project(":platforms:bukkit:runtime"))
    bukkitRuntime(project(":modules:features:update"))
    // Folia scheduler layer is included at runtime; consumers decide whether to activate it
}

tasks.register<ShadowJar>("shadowBukkit") {
    group = "distribution"
    description = "Fat JAR for Bukkit/Spigot/Paper/Folia with relocated Kotlin runtime"
    archiveBaseName = "pnLibrary"
    configurations = listOf(bukkitRuntime)
    applyCommonConfig()
    archiveClassifier = "bukkit-java8"
    filesMatching("plugin.yml") { expand("version" to pnVer) }
}

// ── BungeeCord / Waterfall ────────────────────────────────────────────────────
val bungeeRuntime: Configuration by configurations.creating
dependencies {
    bungeeRuntime(project(":platforms:bungee:runtime"))
    bungeeRuntime(project(":modules:features:update"))
}

tasks.register<ShadowJar>("shadowBungee") {
    group = "distribution"
    description = "Fat JAR for BungeeCord / Waterfall with relocated Kotlin runtime"
    archiveBaseName = "pnLibrary"
    configurations = listOf(bungeeRuntime)
    applyCommonConfig()
    archiveClassifier = "bungeecord-java8"
    filesMatching("bungee.yml") { expand("version" to pnVer) }
}

// ── Velocity ─────────────────────────────────────────────────────────────────
val velocityRuntime: Configuration by configurations.creating
dependencies {
    velocityRuntime(project(":platforms:velocity:runtime"))
    velocityRuntime(project(":modules:features:update"))
}

tasks.register<ShadowJar>("shadowVelocity") {
    group = "distribution"
    description = "Fat JAR for Velocity 3.x with relocated Kotlin runtime"
    archiveBaseName = "pnLibrary"
    configurations = listOf(velocityRuntime)
    applyCommonConfig()
    archiveClassifier = "velocity-java17"
    filesMatching("velocity-plugin.json") { expand("version" to pnVer) }
}

tasks.named("build") {
    dependsOn("shadowBukkit", "shadowBungee", "shadowVelocity", "copyDeveloperArtifacts")
}

val commonJar = project(":modules:common").tasks.named<Jar>("jar")
val commonSources = project(":modules:common").tasks.named<Jar>("sourcesJar")
val localizationJar = project(":modules:features:minecraft-localization").tasks.named<Jar>("jar")
val localizationSources = project(":modules:features:minecraft-localization").tasks.named<Jar>("sourcesJar")
val apiJar = project(":modules:api").tasks.named<Jar>("jar")
val apiSources = project(":modules:api").tasks.named<Jar>("sourcesJar")
val bukkitApiJar = project(":platforms:bukkit:api").tasks.named<Jar>("jar")
val bukkitApiSources = project(":platforms:bukkit:api").tasks.named<Jar>("sourcesJar")
val bungeeApiJar = project(":platforms:bungee:api").tasks.named<Jar>("jar")
val bungeeApiSources = project(":platforms:bungee:api").tasks.named<Jar>("sourcesJar")
val velocityApiJar = project(":platforms:velocity:api").tasks.named<Jar>("jar")
val velocityApiSources = project(":platforms:velocity:api").tasks.named<Jar>("sourcesJar")
tasks.register<Copy>("copyDeveloperArtifacts") {
    group = "distribution"
    description = "Copies the public API binary and sources next to platform distributions"
    dependsOn(commonJar, commonSources, localizationJar, localizationSources, apiJar, apiSources, bukkitApiJar, bukkitApiSources, bungeeApiJar, bungeeApiSources, velocityApiJar, velocityApiSources)
    from(commonJar.flatMap { it.archiveFile })
    from(commonSources.flatMap { it.archiveFile })
    from(localizationJar.flatMap { it.archiveFile })
    from(localizationSources.flatMap { it.archiveFile })
    from(apiJar.flatMap { it.archiveFile })
    from(apiSources.flatMap { it.archiveFile })
    from(bukkitApiJar.flatMap { it.archiveFile })
    from(bukkitApiSources.flatMap { it.archiveFile })
    from(bungeeApiJar.flatMap { it.archiveFile })
    from(bungeeApiSources.flatMap { it.archiveFile })
    from(velocityApiJar.flatMap { it.archiveFile })
    from(velocityApiSources.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("libs"))
}

data class ReleasePlatform(
    val id: String,
    val shadowTask: String,
)

val releasePlatforms = listOf(
    ReleasePlatform("bukkit-java8", "shadowBukkit"),
    ReleasePlatform("bungeecord-java8", "shadowBungee"),
    ReleasePlatform("velocity-java17", "shadowVelocity"),
)
val apiVersionSource = rootProject.file(
    "modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt",
).readText()
val pnApiVersion = Regex("const\\s+val\\s+VERSION\\s*:\\s*Int\\s*=\\s*(\\d+)")
    .find(apiVersionSource)?.groupValues?.get(1)?.toInt()
    ?: error("PnLibraryApi.VERSION was not found")

fun releaseMetadata(platform: String, artifact: String): String = """
    {
      "schemaVersion": 1,
      "id": "pnlibrary",
      "version": "$pnVer",
      "api": {
        "min": $pnApiVersion,
        "max": $pnApiVersion
      },
      "platform": "$platform",
      "artifact": "$artifact"
    }
""".trimIndent() + "\n"

fun installedComponentMetadata(): String = """
    {
      "schema": 1,
      "component": "pnlibrary",
      "version": "$pnVer",
      "pnLibraryApi": {
        "minimum": $pnApiVersion,
        "maximum": $pnApiVersion
      }
    }
""".trimIndent() + "\n"

val releaseSidecarTasks = releasePlatforms.map { platform ->
    val suffix = platform.id.split('-').joinToString("") { part ->
        part.replaceFirstChar(Char::uppercaseChar)
    }
    val generatedDirectory = layout.buildDirectory.dir("generated/pnlibraryMetadata/${platform.id}")
    val generateEmbedded = tasks.register("generate${suffix}Metadata") {
        val outputFile = generatedDirectory.map { it.file("META-INF/pnlibrary/component.json") }
        outputs.file(outputFile)
        doLast {
            outputFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(installedComponentMetadata(), Charsets.UTF_8)
            }
        }
    }
    val shadow = tasks.named<ShadowJar>(platform.shadowTask) {
        dependsOn(generateEmbedded)
        from(generatedDirectory)
    }
    tasks.register("generate${suffix}ReleaseSidecars") {
        dependsOn(shadow, "copyDeveloperArtifacts")
        val jarFile = shadow.flatMap { it.archiveFile }
        val metadataFile = jarFile.map { it.asFile.resolveSibling("${it.asFile.name}.meta.json") }
        val checksumFile = jarFile.map { it.asFile.resolveSibling("${it.asFile.name}.sha256") }
        inputs.file(jarFile)
        outputs.files(metadataFile, checksumFile)
        doLast {
            val jar = jarFile.get().asFile
            metadataFile.get().writeText(releaseMetadata(platform.id, jar.name), Charsets.UTF_8)
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(jar.readBytes())
                .joinToString("") { "%02x".format(it) }
            checksumFile.get().writeText("$hash  ${jar.name}\n", Charsets.UTF_8)
        }
    }
}

val generateAggregateChecksums = tasks.register("generateAggregateChecksums") {
    dependsOn(releaseSidecarTasks)
    val outputFile = layout.buildDirectory.file("libs/checksums.sha256")
    outputs.file(outputFile)
    doLast {
        val lines = releasePlatforms.map { platform ->
            val jarName = "pnLibrary-$pnVer-${platform.id}.jar"
            layout.buildDirectory.file("libs/$jarName.sha256").get().asFile.readText().trim()
        }
        outputFile.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}

tasks.register("verifyReleaseMetadata") {
    group = "verification"
    description = "Verifies release metadata and SHA-256 sidecars for every platform JAR"
    dependsOn(generateAggregateChecksums)
    doLast {
        releasePlatforms.forEach { platform ->
            val jar = layout.buildDirectory.file("libs/pnLibrary-$pnVer-${platform.id}.jar").get().asFile
            val metadata = File(jar.parentFile, "${jar.name}.meta.json")
            val checksum = File(jar.parentFile, "${jar.name}.sha256")
            require(metadata.isFile) { "Missing release metadata: ${metadata.name}" }
            require(checksum.isFile) { "Missing release checksum: ${checksum.name}" }
            require(metadata.readText().contains("\"platform\": \"${platform.id}\"")) {
                "Incorrect platform metadata for ${jar.name}"
            }
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(jar.readBytes())
                .joinToString("") { "%02x".format(it) }
            require(checksum.readText().trim() == "$hash  ${jar.name}") {
                "Incorrect SHA-256 sidecar for ${jar.name}"
            }
            JarFile(jar).use { archive ->
                val embedded = archive.getJarEntry("META-INF/pnlibrary/component.json")
                    ?: error("Missing embedded component metadata in ${jar.name}")
                val json = archive.getInputStream(embedded).bufferedReader(Charsets.UTF_8).use { it.readText() }
                require(json.contains("\"component\": \"pnlibrary\"")) {
                    "Incorrect embedded component identity in ${jar.name}"
                }
                require(json.contains("\"version\": \"$pnVer\"")) {
                    "Incorrect embedded component version in ${jar.name}"
                }
            }
        }
        val aggregate = layout.buildDirectory.file("libs/checksums.sha256").get().asFile
        require(aggregate.isFile && aggregate.readLines().size == releasePlatforms.size) {
            "Aggregate checksums.sha256 must contain every platform artifact"
        }
    }
}

tasks.named("build") {
    dependsOn("verifyReleaseMetadata")
}
