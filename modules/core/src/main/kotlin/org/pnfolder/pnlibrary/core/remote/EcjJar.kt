package org.pnfolder.pnlibrary.core.remote

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** One pinned ECJ build: the size and hash are fixed in code, so a substituted file never gets loaded. */
internal data class EcjBuild(val version: String, val bytes: Long, val sha256: String) {
    val fileName: String get() = "ecj-$version.jar"
    val uri: URI get() = URI("https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/$version/$fileName")

    companion object {
        // 3.26 is the last ECJ that runs on Java 8; newer ones need Java 11 or 17.
        val LEGACY = EcjBuild("3.26.0", 3_133_846L, "ac0ba5876eaf7ebb47749a0d1be179c51f194b9dd0b875d1c09e1b530f5a2db5")
        val MODERN = EcjBuild("3.43.0", 3_329_190L, "c786468c65e906498e7e36ece4e0d04c6d3dd34c9a61b34a3a5b512801911a82")

        fun forJava(feature: Int): EcjBuild = if (feature >= 17) MODERN else LEGACY
    }
}

/** Keeps the ECJ compiler jar in [directory]: downloads it once, then reuses the verified copy. */
internal class EcjJar(
    private val directory: Path,
    private val build: EcjBuild,
    private val open: (URI) -> InputStream = ::connect,
) {
    /** Returns the verified jar, downloading it first when it is missing or damaged. */
    @Synchronized
    fun file(): Path {
        val target = directory.resolve(build.fileName)
        if (isValid(target)) return target
        Files.createDirectories(directory)
        val staging = Files.createTempFile(directory, "ecj-", ".part")
        try {
            open(build.uri).use { Files.copy(it, staging, StandardCopyOption.REPLACE_EXISTING) }
            check(isValid(staging)) { "Downloaded ECJ ${build.version} does not match the expected size or SHA-256" }
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(staging)
        }
        return target
    }

    private fun isValid(path: Path): Boolean =
        Files.isRegularFile(path) && Files.size(path) == build.bytes && sha256(path) == build.sha256

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        fun connect(uri: URI): InputStream {
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            check(connection.responseCode in 200..299) { "ECJ download failed: HTTP ${connection.responseCode}" }
            return connection.inputStream
        }
    }
}
