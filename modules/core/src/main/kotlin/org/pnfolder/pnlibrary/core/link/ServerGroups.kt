package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.io.TsvRecords
import java.nio.file.Path

/**
 * Groups of servers on the proxy, each with the plugins every server of the group should have.
 *
 * A server may be in any number of groups; a group never takes a server away from another. Inside a group one server
 * may differ from the rest: it can skip a plugin the group asks for, or carry one more that the others do not need.
 *
 * The groups live in `groups.tsv` next to `link.key`; every call reads the file and every change writes it back, so
 * the file may also be edited by hand while the proxy runs.
 *
 * @param file where the groups are kept, or null while the proxy has no folder yet
 */
class ServerGroups(private val file: () -> Path?) {
    /**
     * One group: the servers in it and the plugins each of them should have.
     *
     * @property name the name of the group
     * @property servers the servers of the group
     * @property plugins the plugins every server of the group should have
     * @property extra for a server, the plugins it should have besides [plugins]
     * @property skip for a server, the plugins of [plugins] it does not need
     */
    data class Group(
        val name: String,
        val servers: Set<String>,
        val plugins: Set<String>,
        val extra: Map<String, Set<String>> = emptyMap(),
        val skip: Map<String, Set<String>> = emptyMap(),
    ) {
        /** The plugins [server] should have in this group: the common ones, without its skips, and with its extras. */
        fun wanted(server: String): Set<String> {
            val skipped = skip.named(server)
            return (plugins.filterNot { name -> skipped.any { it.equals(name, true) } } + extra.named(server)).toSet()
        }

        /** Whether [server] differs from the rest of the group. */
        fun customized(server: String): Boolean = extra.named(server).isNotEmpty() || skip.named(server).isNotEmpty()

        /** The same group with the plugin set the way [server] should have it: [wanted] is true to ask for it, false to drop it. */
        fun with(server: String, plugin: String, wanted: Boolean): Group {
            val common = plugins.any { it.equals(plugin, true) }
            val extras = extra.named(server).filterNot { it.equals(plugin, true) }.toSet()
            val skips = skip.named(server).filterNot { it.equals(plugin, true) }.toSet()
            val nextExtra = if (wanted && !common) extras + plugin else extras
            val nextSkip = if (!wanted && common) skips + plugin else skips
            return copy(extra = extra.replaced(server, nextExtra), skip = skip.replaced(server, nextSkip))
        }

        /** The same group with [server] in it. */
        fun addServer(server: String): Group = copy(servers = servers + server)

        /** The same group without [server] and without what made it differ. */
        fun removeServer(server: String): Group = copy(servers = servers.filterNot { it.equals(server, true) }.toSet()).reset(server)

        /** The same group where every server has [plugin]. */
        fun addPlugin(plugin: String): Group = copy(plugins = plugins + plugin)

        /** The same group where no server has [plugin], as a common one or as a difference. */
        fun removePlugin(plugin: String): Group = copy(
            plugins = plugins.filterNot { it.equals(plugin, true) }.toSet(),
            extra = extra.mapValues { (_, names) -> names.filterNot { it.equals(plugin, true) }.toSet() }.filterValues { it.isNotEmpty() },
            skip = skip.mapValues { (_, names) -> names.filterNot { it.equals(plugin, true) }.toSet() }.filterValues { it.isNotEmpty() },
        )

        /** The same group without any difference for [server]. */
        fun reset(server: String): Group = copy(extra = extra.replaced(server, emptySet()), skip = skip.replaced(server, emptySet()))

        private fun Map<String, Set<String>>.named(server: String): Set<String> =
            entries.firstOrNull { it.key.equals(server, true) }?.value.orEmpty()

        private fun Map<String, Set<String>>.replaced(server: String, plugins: Set<String>): Map<String, Set<String>> {
            val others = filterKeys { !it.equals(server, true) }
            return if (plugins.isEmpty()) others else others + (server to plugins)
        }
    }

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
        /** Whether this server differs from the rest of the group. */
        val customized: Boolean = false,
    ) {
        /** Whether the server is reachable and has everything, up to date. */
        val ok: Boolean get() = known != null && known.online && missing.isEmpty() && outdated.isEmpty()
    }

    /** Every group, by name. */
    fun all(): List<Group> {
        val servers = linkedMapOf<String, MutableSet<String>>()
        val plugins = linkedMapOf<String, MutableSet<String>>()
        val extra = linkedMapOf<String, MutableMap<String, MutableSet<String>>>()
        val skip = linkedMapOf<String, MutableMap<String, MutableSet<String>>>()
        read().forEach { fields ->
            val group = fields.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@forEach
            servers.getOrPut(group) { linkedSetOf() }
            plugins.getOrPut(group) { linkedSetOf() }
            val value = fields.getOrNull(2).orEmpty()
            val plugin = fields.getOrNull(3).orEmpty()
            when (fields.getOrNull(1)) {
                SERVER -> if (value.isNotEmpty()) servers.getValue(group) += value
                PLUGIN -> if (value.isNotEmpty()) plugins.getValue(group) += value
                EXTRA -> if (value.isNotEmpty() && plugin.isNotEmpty()) extra.getOrPut(group) { linkedMapOf() }.getOrPut(value) { linkedSetOf() } += plugin
                SKIP -> if (value.isNotEmpty() && plugin.isNotEmpty()) skip.getOrPut(group) { linkedMapOf() }.getOrPut(value) { linkedSetOf() } += plugin
            }
        }
        return servers.keys.sortedBy { it.lowercase() }.map {
            Group(it, servers.getValue(it), plugins.getValue(it), extra[it].orEmpty(), skip[it].orEmpty())
        }
    }

    /** The group called [name], ignoring case, or null. */
    fun get(name: String): Group? = all().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** The groups [server] belongs to. */
    fun of(server: String): List<Group> = all().filter { group -> group.servers.any { it.equals(server, true) } }

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
        val wanted = group.wanted(name)
        ServerState(
            name,
            known,
            if (known == null) emptyList() else wanted.filter { plugin -> have.none { it.product.equals(plugin, ignoreCase = true) } },
            have.filter { it.state == UpdateState.UPDATE_AVAILABLE.name && wanted.any { plugin -> plugin.equals(it.product, true) } }
                .map { it.product },
            group.customized(name),
        )
    }

    private fun read(): List<List<String>> = file()?.let(TsvRecords::read).orEmpty()

    private fun write(groups: List<Group>): Boolean {
        val path = file() ?: return false
        val records = groups.flatMap { group ->
            listOf(listOf(group.name, GROUP, "")) +
                group.servers.map { listOf(group.name, SERVER, it) } + group.plugins.map { listOf(group.name, PLUGIN, it) } +
                group.extra.flatMap { (server, names) -> names.map { listOf(group.name, EXTRA, server, it) } } +
                group.skip.flatMap { (server, names) -> names.map { listOf(group.name, SKIP, server, it) } }
        }
        return TsvRecords.write(path, HEADER, records)
    }

    /** Names and kinds used in the file and in commands. */
    companion object {
        /** Name of the groups file. */
        const val FILE = "groups.tsv"
        private const val HEADER = "#pnlibrary-groups 2"
        private const val GROUP = "group"
        private const val SERVER = "server"
        private const val PLUGIN = "plugin"
        private const val EXTRA = "extra"
        private const val SKIP = "skip"
        private const val MAX_NAME = 32
        private val RESERVED = setOf("create", "delete", "of")

        /** Whether [name] may name a group: short, no spaces, and not a word of the command. */
        fun validName(name: String): Boolean =
            name.length in 1..MAX_NAME && name.none { it.isWhitespace() || it.isISOControl() } && name.lowercase() !in RESERVED
    }
}
