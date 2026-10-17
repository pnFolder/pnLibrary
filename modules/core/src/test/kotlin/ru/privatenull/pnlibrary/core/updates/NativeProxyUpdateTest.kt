package ru.privatenull.pnlibrary.core.updates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.updates.engine.*
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Opt-in real platform acceptance. Only existing server software is copied; no production data/configs.
 * The synthetic release changes version metadata, not library bytecode. Catalog downloads and
 * resolver policy are covered separately; this exercises real classloading, commands, restart
 * reconciliation, the packaged offline installer and explicit configuration rollback.
 */
class NativeProxyUpdateTest {
    @Test fun `Velocity applies and rolls back its own library with durable console attribution`() = cycle(true)
    @Test fun `Bungee applies and rolls back its own library with durable console attribution`() = cycle(false)
    @Test @Tag("paper")
    fun `Paper applies and rolls back its own library with durable console attribution`() = cycle(false, true)

    private fun cycle(velocity: Boolean, bukkit: Boolean = false) {
        val label = if (bukkit) "paper" else if (velocity) "velocity" else "bungee"
        val fixture = Paths.get(required("fixtures")).resolve("$label-${UUID.randomUUID()}")
        val plugins = fixture.resolve("plugins").also(Files::createDirectories)
        val data = plugins.resolve(if (velocity) "pnlibrary" else "pnLibrary").also(Files::createDirectories)
        val distribution = Paths.get(required("distribution"))
        val originalVersion = required("version")
        val targetVersion = "99.0.0-dev.1"
        val suffix = if (bukkit) "bukkit-java8" else if (velocity) "velocity-java17" else "bungeecord-java8"
        val original = distribution.resolve("pnLibrary-$originalVersion-$suffix.jar")
        val installed = plugins.resolve("pnLibrary.jar")
        Files.copy(original, installed)
        val serverSource = Paths.get(required(if (bukkit) "paperServerJar" else if (velocity) "velocityServerJar" else "bungeeServerJar"))
        Files.copy(serverSource, fixture.resolve("server.jar"))
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        if (bukkit) configurePaper(fixture, serverSource, port) else configure(fixture, velocity, port)
        val metrics = plugins.resolve("bStats").also(Files::createDirectories)
        Files.write(metrics.resolve("config.yml"), "enabled: false\n".toByteArray())
        val faststats = plugins.resolve("faststats").also(Files::createDirectories)
        Files.write(faststats.resolve("config.properties"), "configVersion=2\nenabled=false\n".toByteArray())
        val configuration = data.resolve("updates.yml")
        val oldConfiguration = "updates:\n  enabled: false\n  library:\n    channel: stable\n    automatic-download: false\n# fixture configuration v1\n"
        Files.write(configuration, oldConfiguration.toByteArray())
        val startedAt = Instant.now()

        val command = if (bukkit) "pn updates" else "pnupdate"
        boot(fixture, velocity, "initial", bukkit).use { server ->
            server.command("$command list", "pnlibrary · $originalVersion")
            server.command("$command history", "История pnLibrary")
            server.command("$command backups", "Резервные наборы pnLibrary")
        }
        val release = fixture.resolve("release.jar")
        syntheticRelease(original, release, originalVersion, targetVersion)
        val product = ProductId.of("pnlibrary")
        val api = ApiVersionRange(PnLibraryApi.VERSION, PnLibraryApi.VERSION)
        val planId = UUID.randomUUID()
        val configs = ConfigurationSnapshots(data.resolve("updates/configurations"), plugins)
        val configId = configs.capture(planId, mapOf(product to UpdateConfigurationBackup(data, listOf("updates.yml"))))
        val transactions = UpdateTransaction(data.resolve("updates/transactions"), ArtifactVerifier(128L * 1024 * 1024), plugins)
        val saved = transactions.prepareForRestart(listOf(TransactionArtifact(
            ArtifactSpecification(product, SemanticVersion.parse(targetVersion), api, "release.jar", Files.size(release), digest(release)),
            release, plugins.resolve("update/pnLibrary.jar"), installed, originalVersion, api, installed,
        )), planId, PnLibraryApi.VERSION, configId)
        assertEquals(digest(original), digest(installed), "Staging must not replace a loaded/installed JAR")
        runUpdater(distribution, originalVersion, data, fixture.resolve("install.log"))
        assertEquals(digest(release), digest(installed))

        boot(fixture, velocity, "updated", bukkit).use { server ->
            server.command("$command list", "pnlibrary · $targetVersion")
            await("Health confirmation", fixture.resolve("updated.log")) {
                TransactionJournal.load(saved.journal).state == TransactionState.COMMITTED
            }
            server.command("$command history", "применено после перезапуска")
            server.command("$command backups", "Резервные наборы pnLibrary")
            val transactionId = saved.journal.parent.fileName.toString()
            server.command("$command rollback $transactionId configs confirm",
                if (bukkit) "Набор $transactionId подготовлен" else "Rollback set $transactionId prepared")
        }
        assertTrue(TransactionJournal.load(saved.journal).restoreConfigurations)
        Files.write(configuration, oldConfiguration.replace("v1", "v2").toByteArray())
        runUpdater(distribution, originalVersion, data, fixture.resolve("rollback.log"))
        assertEquals(digest(original), digest(installed))
        assertEquals(oldConfiguration, String(Files.readAllBytes(configuration)))

        boot(fixture, velocity, "restored", bukkit).use { server ->
            server.command("$command list", "pnlibrary · $originalVersion")
            await("Rollback health confirmation", fixture.resolve("restored.log")) {
                TransactionJournal.load(saved.journal).rollbackHealthVerified
            }
            server.command("$command history", "откат подтверждён после перезапуска")
        }
        val events = UpdateEventJournal(data.resolve("updates/events")).read(pageSize = 100)
        val applied = events.single { it.operation == "HEALTH" && it.planId == planId }
        assertEquals(UpdateActor.Kind.SYSTEM, applied.actor.kind)
        assertEquals(listOf(UpdateEventJournal.Change("pnlibrary", originalVersion, targetVersion)), applied.changes)
        val requested = events.single { it.operation == "ROLLBACK" && it.planId == planId }
        assertEquals(UpdateActor.Kind.CONSOLE, requested.actor.kind)
        assertTrue(requested.actor.id.isNotBlank())
        val restored = events.single { it.operation == "HEALTH_ROLLBACK" && it.planId == planId }
        assertEquals(listOf(UpdateEventJournal.Change("pnlibrary", targetVersion, originalVersion)), restored.changes)
        assertFalse(applied.occurredAt.isBefore(startedAt))
        assertFalse(restored.occurredAt.isBefore(applied.occurredAt))
        assertEquals(TransactionState.ROLLED_BACK, TransactionJournal.load(saved.journal).state)
        println("PASS real $label install/restart/console rollback/config restore: $fixture")
    }

    private fun configurePaper(fixture: Path, server: Path, port: Int) {
        val accepted = Paths.get(required("paperEulaFile"))
        require(Regex("(?m)^eula=true\\s*$").containsMatchIn(String(Files.readAllBytes(accepted)))) {
            "An already accepted Minecraft EULA file must be explicitly supplied"
        }
        Files.copy(accepted, fixture.resolve("eula.txt"))
        // Copy software caches only; never worlds, player data, plugins or server configuration.
        listOf("cache", "libraries", "versions").forEach { name ->
            val source = server.parent.resolve(name)
            if (Files.isDirectory(source)) Files.walk(source).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar") && !Files.isSymbolicLink(it) }
                    .forEach { path ->
                        val target = fixture.resolve(name).resolve(source.relativize(path))
                        Files.createDirectories(target.parent)
                        Files.copy(path, target)
                    }
            }
        }
        Files.write(fixture.resolve("server.properties"), """
            server-ip=127.0.0.1
            server-port=$port
            online-mode=true
            enable-query=false
            enable-rcon=false
            enable-status=false
            max-players=1
            level-name=verification
            level-type=minecraft:flat
            generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}
            generate-structures=false
            view-distance=2
            simulation-distance=2
            spawn-chunk-radius=0
        """.trimIndent().toByteArray())
    }

    private fun syntheticRelease(source: Path, target: Path, before: String, after: String) {
        val descriptors = setOf("velocity-plugin.json", "bungee.yml", "plugin.yml", "META-INF/pnlibrary/component.json")
        var replaced = 0
        ZipFile(source.toFile()).use { zip ->
            ZipOutputStream(Files.newOutputStream(target)).use { output ->
                zip.entries().asSequence().forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { input ->
                        if (entry.name in descriptors) {
                            val text = input.bufferedReader().readText()
                            if (before in text) replaced++
                            output.write(text.replace(before, after).toByteArray())
                        } else input.copyTo(output)
                    }
                    output.closeEntry()
                }
            }
        }
        assertTrue(replaced >= 2, "Expected native and component version descriptors")
    }

    private fun configure(fixture: Path, velocity: Boolean, port: Int) {
        val content = if (velocity) """
            config-version = "2.8"
            bind = "127.0.0.1:$port"
            online-mode = true
            player-info-forwarding-mode = "NONE"
            forwarding-secret-file = "forwarding.secret"
            [servers]
            fixture = "127.0.0.1:1"
            try = ["fixture"]
            [forced-hosts]
            "lobby.example.com" = ["fixture"]
            "factions.example.com" = ["fixture"]
            "minigames.example.com" = ["fixture"]
        """.trimIndent() else """
            online_mode: true
            servers:
              fixture:
                address: 127.0.0.1:1
                restricted: true
            listeners:
            - host: 127.0.0.1:$port
              query_enabled: false
              priorities: [fixture]
              force_default_server: true
              forced_hosts: {}
            disabled_commands: []
        """.trimIndent()
        Files.write(fixture.resolve(if (velocity) "velocity.toml" else "config.yml"), content.toByteArray())
        if (!velocity) Files.write(fixture.resolve("modules.yml"), "version: 2\nmodules: []\n".toByteArray())
    }

    private fun boot(fixture: Path, velocity: Boolean, phase: String, bukkit: Boolean = false): Server {
        val log = fixture.resolve("$phase.log")
        val arguments = mutableListOf(java(), "-Xms128M", "-Xmx1G", "-Dfile.encoding=UTF-8",
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dterminal.jline=false",
            "-Dterminal.ansi=false", "-jar", "server.jar")
        if (bukkit) arguments.add("--nogui")
        val process = ProcessBuilder(arguments).directory(fixture.toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        val server = Server(process, log, if (bukkit) "stop" else if (velocity) "shutdown" else "end")
        try {
            await("Platform readiness", log) { server.output().contains(if (velocity || bukkit) "Done (" else "Listening on ") }
            return server
        } catch (error: Throwable) { server.close(); throw error }
    }

    private inner class Server(val process: Process, val log: Path, val stop: String) : AutoCloseable {
        fun output(): String = if (Files.exists(log)) String(Files.readAllBytes(log)) else ""
        fun command(command: String, expected: String) {
            val offset = output().length
            process.outputStream.write((command + "\n").toByteArray())
            process.outputStream.flush()
            await("Command $command -> $expected", log) { output().drop(offset).contains(expected) }
        }
        override fun close() {
            if (!process.isAlive) return
            try {
                process.outputStream.write((stop + "\n").toByteArray())
                process.outputStream.flush()
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Proxy failed to stop: $log")
                assertEquals(0, process.exitValue(), output())
            } finally { if (process.isAlive) { process.destroyForcibly(); process.waitFor() } }
        }
    }

    private fun runUpdater(distribution: Path, version: String, data: Path, log: Path) {
        val process = ProcessBuilder(java(), "-jar", distribution.resolve("pnLibrary-updater-$version.jar").toString(),
            "--server-stopped", data.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Offline updater timed out: $log")
            assertEquals(0, process.exitValue(), String(Files.readAllBytes(log)))
        } finally { if (process.isAlive) { process.destroyForcibly(); process.waitFor() } }
    }

    private fun await(label: String, log: Path, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        while (System.nanoTime() < deadline) { if (condition()) return; Thread.sleep(100) }
        fail<Unit>("$label timed out; $log\n${if (Files.exists(log)) String(Files.readAllBytes(log)) else "no log"}")
    }

    private fun required(name: String): String = requireNotNull(System.getProperty("pnlibrary.native.$name"))
    private fun java(): String = Paths.get(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString()
    private fun digest(path: Path): String {
        val sha = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input -> val buffer = ByteArray(65536); while (true) {
            val count = input.read(buffer); if (count < 0) break; sha.update(buffer, 0, count)
        } }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
}
