package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.io.TsvRecords
import java.nio.file.Path

/**
 * Groups of servers on the proxy, each with the plugins every server of the group should have.
 *
 * The groups live in `groups.tsv` next to `link.key`; every call reads the file and every change writes it back, so
 * the file may also be edited by hand while the proxy runs.
 *
 * @param file where the groups are kept, or null while the proxy has no folder yet
 */
class ServerGroups(private val file: () -> Path?) {
    /** One group: the servers in it and the plugins each of them should have. */
    data class Group(val name: String, val servers: Set<String>, val plugins: Set<String>)

    /** How one server of a group compares with what the group asks for. */
    data class ServerState(
        /** The server name as the group lists it. */
        val server: String,
        /** The server's report, or null if it never reported. */
        val known: NetworkServer?,
        /** Plugins the group asks for that the server does not have. */
        val missing: List<String>,
        /** Plugins of the group that have a newer version available on the server. */
        val outdated: List<String>,
    ) {
        /** Whether the server is reachable and has everything, up to date. */
        val ok: Boolean get() = known != null && known.online && missing.isEmpty() && outdated.isEmpty()
    }

    /** Every group, by name. */
    fun all(): List<Group> {
        val servers = linkedMapOf<String, MutableSet<String>>()
        val plugins = linkedMapOf<String, MutableSet<String>>()
        read().forEach { fields ->
            val group = fields.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@forEach
            servers.getOrPut(group) { linkedSetOf() }
            plugins.getOrPut(group) { linkedSetOf() }
            val value = fields.getOrNull(2).orEmpty()
            when (fields.getOrNull(1)) {
                SERVER -> if (value.isNotEmpty()) servers.getValue(group) += value
                PLUGIN -> if (value.isNotEmpty()) plugins.getValue(group) += value
            }
        }
        return servers.keys.sortedBy { it.lowercase() }.map { Group(it, servers.getValue(it), plugins.getValue(it)) }
    }

    /** The group called [name], ignoring case, or null. */
    fun get(name: String): Group? = all().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Makes an empty group; returns false when the name is taken, reserved or unfit. */
    fun create(name: String): Boolean {
        if (!validName(name) || get(name) != null) return false
        return write(all() + Group(name, emptySet(), emptySet()))
    }

    /** Deletes the group called [name]; returns whether there was one. */
    fun delete(name: String): Boolean {
        val groups = all()
        val group = groups.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        return write(groups - group)
    }

    /** Replaces the group called [name] with [change] of it; returns false when there is no such group. */
    fun edit(name: String, change: (Group) -> Group): Boolean {
        val groups = all()
        val group = groups.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        return write(groups.map { if (it === group) change(group).copy(name = group.name) else it })
    }

    /** Compares each server of [group] with its report in [servers]. */
    fun compare(group: Group, servers: List<NetworkServer>): List<ServerState> = group.servers.sortedBy { it.lowercase() }.map { name ->
        val known = servers.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val have = known?.report?.plugins.orEmpty()
        ServerState(
            name,
            known,
            if (known == null) emptyList() else group.plugins.filter { wanted -> have.none { it.product.equals(wanted, ignoreCase = true) } },
            have.filter { it.state == UpdateState.UPDATE_AVAILABLE.name && group.plugins.any { wanted -> wanted.equals(it.product, true) } }
                .map { it.product },
        )
    }

    private fun read(): List<List<String>> = file()?.let(TsvRecords::read).orEmpty()

    private fun write(groups: List<Group>): Boolean {
        val path = file() ?: return false
        val records = groups.flatMap { group ->
            listOf(listOf(group.name, GROUP, "")) +
                group.servers.map { listOf(group.name, SERVER, it) } + group.plugins.map { listOf(group.name, PLUGIN, it) }
        }
        return TsvRecords.write(path, HEADER, records)
    }

    /** Names and kinds used in the file and in commands. */
    companion object {
        /** Name of the groups file. */
        const val FILE = "groups.tsv"
        private const val HEADER = "#pnlibrary-groups 1"
        private const val GROUP = "group"
        private const val SERVER = "server"
        private const val PLUGIN = "plugin"
        private const val MAX_NAME = 32
        private val RESERVED = setOf("create", "delete")

        /** Whether [name] may name a group: short, no spaces, and not a word of the command. */
        fun validName(name: String): Boolean =
            name.length in 1..MAX_NAME && name.none { it.isWhitespace() || it.isISOControl() } && name.lowercase() !in RESERVED
    }
}
