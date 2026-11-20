package org.pnfolder.pnlibrary.core.downloads.dependencies

import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.DownloadState
import java.net.URI
import java.security.SecureRandom

/** One plugin that is not installed (or too old): what it is, why it is needed, and the addresses it can be had from. */
internal data class MissingDependency(
    val plugin: String,
    val minimum: String,
    val page: URI?,
    val sources: List<URI>,
    val title: String = plugin,
    val description: String? = null,
    val required: Boolean = true,
    /** `true` when the author gave a SHA-256 the file is checked against; else the file is taken as downloaded. */
    val checked: Boolean = false,
    /** Name of the chosen file when the plugin has several for different servers. */
    val variant: String? = null,
    /** `true` when the plugin has files, but none is made for this platform or Minecraft version. */
    val noFileForServer: Boolean = false,
)

/** The missing dependencies of one plugin; [code] confirms their installation from the console. */
internal class MissingGroup(
    val owner: String,
    val code: String,
    val items: List<MissingDependency>,
    private val registration: DownloadRegistration?,
) {
    val installable: Boolean get() = registration != null
    val addresses: List<URI> get() = items.flatMap { it.sources }

    /** Not yet staged: still worth telling the administrator about. */
    val pending: Boolean
        get() = registration?.snapshots()?.let { shots -> shots.isEmpty() || shots.any { it.state != DownloadState.STAGED } } ?: true

    fun install() = requireNotNull(registration) { "У этих зависимостей нет прямой ссылки" }.downloadNow()
}

/** Missing dependencies waiting for the administrator, found by the code printed with them. */
internal class MissingDependencies {
    private val groups = ArrayList<MissingGroup>()
    private val random = SecureRandom()

    @Synchronized
    fun add(owner: String, items: List<MissingDependency>, registration: DownloadRegistration?): MissingGroup {
        val group = MissingGroup(owner, newCode(), items, registration)
        groups += group
        return group
    }

    @Synchronized
    fun pending(): List<MissingGroup> = groups.filter { it.pending }

    @Synchronized
    fun find(code: String): MissingGroup? = groups.firstOrNull { it.pending && it.code.equals(code.trim(), ignoreCase = true) }

    private fun newCode(): String = (0 until CODE_PARTS).joinToString("-") {
        (0 until PART_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }

    private companion object {
        const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
        const val CODE_PARTS = 4
        const val PART_LENGTH = 4
    }
}
