package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.core.logging.PlatformLoggingService


import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.jar.JarFile
import java.security.MessageDigest

/** Mandatory pnLibrary updater. Only the release channel is configurable. */
object MandatoryUpdateService {
    private const val MAX_BYTES = 512L * 1024L * 1024L
    private const val CHECK_INTERVAL_MS = 30L * 60L * 1000L
    private const val NOTIFICATION_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private val lastAnnouncements = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun start(owner: Any, platform: PlatformAdapter, currentVersion: String, artifact: String, currentJar: Path, updateDir: Path): AutoCloseable {
        val settings = loadSettings(platform.dataFolder ?: currentJar.parent.resolve("pnLibrary"))
        val thread = startProduct(owner, platform, currentVersion, "pnFolder", "pnLibrary", settings.channel,
            "(?i)^pnLibrary-$artifact-.*\\.jar$", currentJar, updateDir, settings.automaticDownload, 8) { }
        return AutoCloseable { thread.interrupt() }
    }

    internal fun startProduct(owner: Any, platform: PlatformAdapter, currentVersion: String,
        repositoryOwner: String, repositoryName: String, channel: String, assetPattern: String,
        currentJar: Path, updateDir: Path, automaticDownload: Boolean, minimumJava: Int,
        observer: (UpdateSnapshot) -> Unit): Thread = Thread({ while (true) {
            runCatching { check(owner, platform, currentVersion, repositoryOwner, repositoryName, channel, assetPattern, currentJar, updateDir, automaticDownload, minimumJava, observer) }
                .onFailure {
                    observer(snapshot(repositoryName, currentVersion, null, channel, UpdateState.FAILED,
                        minimumJava, automaticDownload, null, it.message))
                    platform.log(owner, LogLevel.WARNING, "[pnLibrary] Не удалось проверить обновления: ${it.message}")
                }
            try { Thread.sleep(CHECK_INTERVAL_MS) } catch (_: InterruptedException) { return@Thread }
        } },
            "pnLibrary-updater-$repositoryName").apply { isDaemon = true; start() }

    private fun check(owner: Any, platform: PlatformAdapter, currentVersion: String,
        repositoryOwner: String, repositoryName: String, channel: String, assetPattern: String,
        currentJar: Path, updateDir: Path, automaticDownload: Boolean, minimumJava: Int,
        observer: (UpdateSnapshot) -> Unit) {
        observer(snapshot(repositoryName, currentVersion, null, channel, UpdateState.CHECKING,
            minimumJava, automaticDownload, null, null))
        val releasesUrl = "https://api.github.com/repos/$repositoryOwner/$repositoryName/releases?per_page=30"
        val releases = JsonParser.parseString(request(releasesUrl)).asJsonArray
        val candidate = releases.map { it.asJsonObject }
            .filter { !it["draft"].asBoolean }
            .filter { allowed(it["tag_name"].asString, it["prerelease"].asBoolean, channel) }
            .mapNotNull { release -> SemanticVersion.tryParse(release["tag_name"].asString)?.let { it to release } }
            .maxByOrNull { it.first }
            ?.second
        if (candidate == null) {
            observer(snapshot(repositoryName, currentVersion, null, channel, UpdateState.CURRENT,
                minimumJava, automaticDownload, null, null))
            return
        }
        val latest = candidate["tag_name"].asString.removePrefix("v")
        val current = SemanticVersion.parse(currentVersion)
        val newest = SemanticVersion.parse(latest)
        if (newest <= current) {
            observer(snapshot(repositoryName, currentVersion, latest, channel, UpdateState.CURRENT,
                minimumJava, automaticDownload, candidate["html_url"].asString, null))
            return
        }
        if (!automaticDownload) {
            observer(snapshot(repositoryName, currentVersion, latest, channel, UpdateState.AVAILABLE,
                minimumJava, false, candidate["html_url"].asString, null))
            if (shouldAnnounce("$repositoryOwner/$repositoryName:$latest:available")) {
                PlatformLoggingService(platform).showUpdateAvailableNotice(
                    owner, repositoryName, currentVersion, latest, channel, minimumJava,
                    Runtime.version().feature(), candidate["html_url"].asString,
                )
            }
            return
        }
        val asset = candidate["assets"].asJsonArray.map { it.asJsonObject }.firstOrNull {
            Regex(assetPattern).matches(it["name"].asString)
        } ?: error("В релизе $latest нет подходящего JAR")
        val checksums = candidate["assets"].asJsonArray.map { it.asJsonObject }.firstOrNull {
            it["name"].asString.equals("checksums.sha256", true)
        } ?: error("В релизе $latest отсутствует checksums.sha256")
        Files.createDirectories(updateDir)
        val temp = Files.createTempFile(updateDir, "$repositoryName-", ".tmp")
        try {
            download(asset["browser_download_url"].asString, temp)
            require(Files.size(temp) in 1..MAX_BYTES) { "Некорректный размер обновления" }
            val manifest = request(checksums["browser_download_url"].asString)
            val assetName = asset["name"].asString
            val expected = manifest.lineSequence().map { it.trim() }.firstOrNull { it.endsWith(assetName) }
                ?.substringBefore(' ')?.lowercase(Locale.ROOT)
                ?: error("В checksums.sha256 отсутствует $assetName")
            require(sha256(temp) == expected) { "SHA-256 обновления не совпадает" }
            val distribution = when (platform.type) {
                PlatformType.BUKKIT -> "bukkit"
                PlatformType.BUNGEECORD -> "bungee"
                PlatformType.VELOCITY -> "velocity"
            }
            validateJar(temp, distribution)
            val target = updateDir.resolve(currentJar.fileName.toString())
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            observer(snapshot(repositoryName, currentVersion, latest, channel, UpdateState.DOWNLOADED,
                minimumJava, automaticDownload, candidate["html_url"].asString, null))
            if (shouldAnnounce("$repositoryOwner/$repositoryName:$latest:downloaded")) {
                PlatformLoggingService(platform).showUpdateNotice(
                    owner, repositoryName, currentVersion, latest, channel,
                    minimumJava, Runtime.version().feature(),
                    candidate["html_url"].asString,
                )
            }
        } finally { Files.deleteIfExists(temp) }
    }

    private fun shouldAnnounce(key: String): Boolean {
        val now = System.currentTimeMillis()
        var announce = false
        lastAnnouncements.compute(key) { _, previous ->
            if (previous == null || now - previous >= NOTIFICATION_INTERVAL_MS) {
                announce = true
                now
            } else previous
        }
        return announce
    }

    internal fun checkOnce(owner: Any, platform: PlatformAdapter, currentVersion: String,
        repositoryOwner: String, repositoryName: String, channel: String, assetPattern: String,
        currentJar: Path, updateDir: Path, download: Boolean, minimumJava: Int,
        observer: (UpdateSnapshot) -> Unit) {
        check(owner, platform, currentVersion, repositoryOwner, repositoryName, channel, assetPattern,
            currentJar, updateDir, download, minimumJava, observer)
    }

    private fun snapshot(product: String, current: String, latest: String?, channel: String,
        state: UpdateState, minimumJava: Int, automatic: Boolean, url: String?, message: String?) = UpdateSnapshot(
        product, current, latest, UpdateChannel.valueOf(channel.uppercase(Locale.ROOT)), state,
        Runtime.version().feature(), minimumJava, automatic, url, message,
    )

    private data class UpdateSettings(val channel: String, val automaticDownload: Boolean)

    private fun loadSettings(folder: Path): UpdateSettings {
        Files.createDirectories(folder)
        val file = folder.resolve("updates.yml")
        if (!Files.exists(file)) Files.write(file, listOf(
            "# Канал обязательных автоматических обновлений pnLibrary.",
            "# stable — только стабильные релизы (рекомендуется).",
            "# beta   — стабильные и beta-релизы.",
            "# alpha  — все релизы, включая экспериментальные alpha.",
            "# Автоматическую загрузку можно отключить; проверка и уведомления останутся активными.",
            "channel: stable",
            "auto-download: true",
        ), StandardCharsets.UTF_8)
        val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
        val value = lines.firstOrNull { it.trim().startsWith("channel:") }
            ?.substringAfter(':')?.trim()?.lowercase(Locale.ROOT)
        val auto = lines.firstOrNull { it.trim().startsWith("auto-download:") }
            ?.substringAfter(':')?.trim()?.equals("true", true) ?: true
        return UpdateSettings(value?.takeIf { it in setOf("stable", "beta", "alpha") } ?: "stable", auto)
    }

    private fun allowed(tag: String, prerelease: Boolean, channel: String): Boolean {
        val lower = tag.lowercase(Locale.ROOT)
        return when (channel) {
            "alpha" -> true
            "beta" -> !lower.contains("alpha")
            else -> !prerelease && !lower.contains("alpha") && !lower.contains("beta") && !lower.contains("rc")
        }
    }

    private fun request(url: String): String = connection(url).run {
        inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.also { disconnect() }
    }

    private fun download(url: String, target: Path) {
        val connection = connection(url)
        val declared = connection.contentLengthLong
        require(declared < 0 || declared <= MAX_BYTES) { "Обновление превышает 512 МБ" }
        connection.inputStream.use { input -> Files.newOutputStream(target).use { output ->
            val buffer = ByteArray(16 * 1024); var total = 0L
            while (true) { val read = input.read(buffer); if (read < 0) break; total += read
                require(total <= MAX_BYTES) { "Обновление превышает 512 МБ" }; output.write(buffer, 0, read) }
        } }
        connection.disconnect()
    }

    private fun validateJar(file: Path, artifact: String) = JarFile(file.toFile()).use { jar ->
        val descriptor = when (artifact) {
            "bukkit" -> "plugin.yml"
            "bungee" -> "bungee.yml"
            else -> "velocity-plugin.json"
        }
        require(jar.getJarEntry(descriptor) != null) { "Скачанный файл не является плагином для $artifact" }
    }

    private fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input -> val buffer = ByteArray(16 * 1024); while (true) {
            val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun connection(url: String): HttpURLConnection = (java.net.URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000; readTimeout = 20_000; instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "pnLibrary-Updater")
        require(responseCode in 200..299) { "GitHub вернул HTTP $responseCode" }
    }

}
