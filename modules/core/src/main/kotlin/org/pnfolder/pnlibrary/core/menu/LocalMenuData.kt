package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.events.EventDay
import org.pnfolder.pnlibrary.api.events.EventDescription
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.events.EventTrace
import org.pnfolder.pnlibrary.api.events.ListenerInfo
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.core.lifecycle.JournalDetail
import org.pnfolder.pnlibrary.core.lifecycle.JournalGroup
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl
import org.pnfolder.pnlibrary.core.lifecycle.cancelTask
import org.pnfolder.pnlibrary.core.lifecycle.journalDetail
import org.pnfolder.pnlibrary.core.lifecycle.journalGroups
import org.pnfolder.pnlibrary.core.lifecycle.runTaskNow
import org.pnfolder.pnlibrary.core.lifecycle.taskSnapshots
import org.pnfolder.pnlibrary.core.link.ServerGroups
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateStatusMessages
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage

/**
 * The facts of the library on this server. It is the source of this server's own menu, and the one that answers when
 * another server asks over the link.
 *
 * @param library this server's library
 * @param directory the plugins of this platform
 * @param run runs a chat command for a viewer and returns what it printed
 * @param groups the groups of servers, on a proxy; null on a game server
 */
class LocalMenuData(
    private val library: PnLibrary,
    private val directory: PluginDirectory,
    private val run: (ViewerInfo, String) -> List<Component> = { _, _ -> emptyList() },
    private val groups: ServerGroups? = null,
) : MenuData {
    private val bus get() = library.events.inspector

    override fun version(): String = library.version

    override fun snapshots(): List<UpdateSnapshot> = library.updates.all().map { it.snapshot }.sortedBy { it.product.lowercase() }

    override fun snapshot(product: String): UpdateSnapshot? = library.updates.get(product)?.snapshot

    override fun plugins(): List<PluginRow> = library.plugins.all().map { registration ->
        val owner = directory.owner(registration.owner)
        PluginRow(owner?.name ?: registration.owner.javaClass.simpleName, owner?.version, registration.all().size)
    }

    override fun plan(): PlanInfo? = library.updates.currentPlan().orElse(null)?.let { plan ->
        PlanInfo(plan.state, plan.plan?.changes?.isNotEmpty() == true, plan.blockers.map { UpdateStatusMessages.blocker(it) })
    }

    override fun heldUntil(product: String): Instant? = library.updates.heldUntil(product)

    override fun historyCount(product: String?): CompletionStage<Int> = library.updates.historyCount(product)

    override fun historyPage(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>> = library.updates.historyPage(page, product)

    override fun backups(): CompletionStage<List<UpdateBackup>> = library.updates.backups()

    override fun checkAll(actor: UpdateActor): CompletionStage<Boolean> = library.updates.checkNow(actor).thenApply { true }

    override fun checkOne(product: String, actor: UpdateActor) {
        library.updates.get(product)?.checkNow()
    }

    override fun setAutomatic(product: String, enabled: Boolean, actor: UpdateActor): CompletionStage<Boolean> =
        library.updates.setAutomaticDownload(product, enabled, actor).thenApply { true }

    override fun setChannel(product: String, channel: UpdateChannel?, actor: UpdateActor): CompletionStage<Boolean> =
        library.updates.setChannel(product, channel, actor).thenApply { true }

    override fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<Boolean> =
        library.updates.setHeld(product, duration, actor).thenApply { true }

    override fun rollback(id: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<Boolean> =
        library.updates.rollback(id, restoreConfigurations, actor).thenApply { true }

    override fun journalGroups(level: String?, hours: Int, limit: Int): List<JournalGroup> = library.journalGroups(level, hours, limit)

    override fun journalDetail(id: String): JournalDetail? = library.journalDetail(id)

    override fun tasks(): List<TaskSnapshot> = library.taskSnapshots()

    override fun runTask(id: String): TaskControl = library.runTaskNow(id)

    override fun cancelTask(id: String): TaskControl = library.cancelTask(id)

    override fun events(): List<EventInfo> = bus.events()

    override fun listeners(): List<ListenerInfo> = bus.listeners()

    override fun eventHistory(eventType: String?): List<EventDay> = bus.history(eventType)

    override fun eventHistoryDays(): Int = bus.historyDays

    override fun tracedEvents(): List<String> = bus.tracedEvents()

    override fun traces(): List<EventTrace> = bus.traces()

    override fun clearTraces() = bus.clearTraces()

    override fun setTracing(eventType: String, enabled: Boolean) {
        val type = bus.eventType(eventType) ?: return
        if (enabled) bus.enableTracing(type) else bus.disableTracing(type)
    }

    override fun exportEventHistory(json: Boolean): String? = bus.exportHistory(json)?.toString()

    override fun eventAbout(eventType: String): EventAbout? {
        val type = bus.eventType(eventType) ?: return null
        val about = type.getAnnotation(EventDescription::class.java)
        return EventAbout(about?.purpose.orEmpty(), about?.usage.orEmpty(), directory.ownerOf(type))
    }

    override fun ownerNamed(name: String): OwnerInfo? = directory.ownerNamed(name)

    override fun groups(): List<ServerGroups.Group> = groups?.all().orEmpty()

    override fun editGroup(edit: GroupEdit): Boolean {
        val store = groups ?: return false
        val server = edit.server.orEmpty()
        val plugin = edit.plugin.orEmpty()
        return when (edit.op) {
            GroupOp.CREATE -> store.create(edit.group)
            GroupOp.DELETE -> store.delete(edit.group)
            GroupOp.ADD_SERVER -> store.edit(edit.group) { it.addServer(server) }
            GroupOp.REMOVE_SERVER -> store.edit(edit.group) { it.removeServer(server) }
            GroupOp.ADD_PLUGIN -> store.edit(edit.group) { it.addPlugin(plugin) }
            GroupOp.REMOVE_PLUGIN -> store.edit(edit.group) { it.removePlugin(plugin) }
            GroupOp.NEED -> store.edit(edit.group) { it.with(server, plugin, true) }
            GroupOp.SKIP -> store.edit(edit.group) { it.with(server, plugin, false) }
            GroupOp.RESET -> store.edit(edit.group) { it.reset(server) }
        }
    }

    override fun command(viewer: ViewerInfo, line: String): List<Component> = run(viewer, line)
}
