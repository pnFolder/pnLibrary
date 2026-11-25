package org.pnfolder.pnlibrary.api.dependencies.external

import org.pnfolder.pnlibrary.api.downloads.source.isHttps
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.requireSha256
import java.net.URI

/**
 * One downloadable file of an external plugin, with its own checksum and the servers it is meant for.
 *
 * The checksum belongs to this file only: when this variant is chosen, exactly this file is downloaded and checked against
 * exactly this SHA-256. Variants for other platforms or versions are never touched.
 *
 * @property uri verified HTTPS artifact location
 * @property sha256 expected SHA-256 digest, when known
 * @property mirrors other HTTPS addresses of the same file, tried in order when the one before does not answer
 * @property platforms platforms this file fits; empty means every platform
 * @property minecraftFrom oldest Minecraft version this file fits (inclusive), such as `1.16`; null means no lower bound
 * @property minecraftBefore first Minecraft version this file no longer fits (exclusive), such as `1.21`; null means no upper bound
 * @property label short name of the variant for people, such as `для Paper 1.21`
 * @property maxBytes largest accepted size of this file in bytes; null means the library-wide limit
 */
class ExternalArtifact(
    val uri: URI,
    val sha256: String?,
    val mirrors: List<URI> = emptyList(),
    val platforms: Set<PlatformType> = emptySet(),
    val minecraftFrom: String? = null,
    val minecraftBefore: String? = null,
    val label: String? = null,
    val maxBytes: Long? = null,
) {
    init {
        require(uri.isHttps()) { "External artifact must use HTTPS" }
        requireSha256(sha256, "External artifact")
        require(maxBytes == null || maxBytes in 1..Int.MAX_VALUE.toLong()) { "Size limit must be between 1 byte and 2 GB" }
        require(mirrors.all { it.isHttps() }) { "External artifact mirrors must use HTTPS" }
        minecraftFrom?.let { requireNotNull(numbers(it)) { "Minecraft version must look like 1.20.4: $it" } }
        minecraftBefore?.let { requireNotNull(numbers(it)) { "Minecraft version must look like 1.20.4: $it" } }
    }

    /** Whether this file fits a server of [platform] running Minecraft [minecraft] (null when unknown). */
    fun fits(platform: PlatformType, minecraft: String?): Boolean {
        if (platforms.isNotEmpty() && platform !in platforms) return false
        if (minecraftFrom == null && minecraftBefore == null) return true
        val current = minecraft?.let(::numbers) ?: return false
        minecraftFrom?.let { if (compare(current, numbers(it)!!) < 0) return false }
        minecraftBefore?.let { if (compare(current, numbers(it)!!) >= 0) return false }
        return true
    }

    private companion object {
        fun numbers(text: String): List<Int>? {
            val parts = Regex("^\\d+(\\.\\d+)*").find(text.trim())?.value?.split('.') ?: return null
            return parts.map { it.toIntOrNull() ?: return null }
        }

        fun compare(left: List<Int>, right: List<Int>): Int {
            for (i in 0 until maxOf(left.size, right.size)) {
                val order = (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 })
                if (order != 0) return order
            }
            return 0
        }
    }
}
