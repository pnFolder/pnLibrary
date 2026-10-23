package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import java.time.Instant
import java.time.ZoneId

/**
 * Chat and console pages of the managed update catalog, in the shared [ChatKit] style.
 *
 * Each page is rendered by its own screen ([CatalogScreen], [HistoryScreen], [BackupsScreen], [ProductScreens]);
 * this object is the single entry point for commands. Nothing here calls the server or changes updates.
 */
object UpdateCatalogView {
    /** Entries per page, shared with the history reader. */
    const val PAGE_SIZE = 10

    /** Channels a component can be switched between, in order of increasing risk. */
    val CHANNELS: List<UpdateChannel> = UpdateChannel.values().toList()

    /** Pause periods offered on the settings card, as accepted by `set <plugin> hold`; a pause lasts at most a week. */
    val HOLD_PERIODS: List<String> = listOf("1h", "6h", "1d", "3d", "7d")

    /**
     * Renders one page of registered components, never the native plugin inventory.
     *
     * @param now reference for release ages in the hover cards
     */
    fun catalog(
        snapshots: List<UpdateSnapshot>,
        page: Int,
        updatesOnly: Boolean,
        routes: UpdateCatalogRoutes,
        now: Instant = Instant.now(),
        zone: ZoneId = org.pnfolder.pnlibrary.core.ui.DisplayZone.zone,
    ): List<Component> = CatalogScreen(routes, now, zone).catalog(snapshots, page, updatesOnly)

    /**
     * Renders durable events newest first, grouped by day.
     *
     * @param totalPages number of pages when known; otherwise only [entries] being a full page implies another
     * @param product when set, the title shows the filter and the lines do not offer to filter again
     * @param now reference for "today" and "yesterday"
     * @param zone zone used for days and clock times
     */
    fun history(
        entries: List<UpdateHistoryEntry>,
        page: Int,
        routes: UpdateCatalogRoutes,
        totalPages: Int? = null,
        product: String? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = org.pnfolder.pnlibrary.core.ui.DisplayZone.zone,
    ): List<Component> = HistoryScreen(routes, now, zone).history(entries, page, totalPages, product)

    /** Renders retained rollback sets with their age; paths and file contents are never shown. */
    fun backups(
        backups: List<UpdateBackup>,
        page: Int,
        routes: UpdateCatalogRoutes,
        now: Instant = Instant.now(),
        zone: ZoneId = org.pnfolder.pnlibrary.core.ui.DisplayZone.zone,
    ): List<Component> = BackupsScreen(routes, now, zone).backups(backups, page)

    /**
     * Renders the details card of one component: versions, release date, other channels and its pause.
     *
     * @param heldUntil when the current pause ends, from [org.pnfolder.pnlibrary.api.updates.UpdateService.heldUntil]
     */
    fun info(
        snapshot: UpdateSnapshot,
        routes: UpdateCatalogRoutes,
        heldUntil: Instant? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = org.pnfolder.pnlibrary.core.ui.DisplayZone.zone,
    ): List<Component> = ProductScreens(routes, now, zone).info(snapshot, heldUntil)

    /** Renders the settings card of one component: channel, automatic download and a pause for a chosen period. */
    fun settings(
        snapshot: UpdateSnapshot,
        routes: UpdateCatalogRoutes,
        heldUntil: Instant? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = org.pnfolder.pnlibrary.core.ui.DisplayZone.zone,
    ): List<Component> = ProductScreens(routes, now, zone).settings(snapshot, heldUntil)

    /**
     * Renders the state of the server and every registered component, for `/pn status`.
     *
     * @param facts labelled values shown before the components, such as the server core and Java
     * @param support link opened by the support button, or null to hide it
     */
    fun status(
        facts: List<Pair<String, String>>,
        snapshots: List<UpdateSnapshot>,
        routes: UpdateCatalogRoutes,
        support: String? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = org.pnfolder.pnlibrary.core.ui.DisplayZone.zone,
    ): List<Component> = CatalogScreen(routes, now, zone).status(facts, snapshots, support)

    /**
     * Renders the notice an administrator gets on joining: only the components with an available or prepared
     * update, or nothing when there are none.
     */
    fun announcement(
        snapshots: List<UpdateSnapshot>,
        routes: UpdateCatalogRoutes,
        support: String? = null,
        now: Instant = Instant.now(),
        zone: ZoneId = org.pnfolder.pnlibrary.core.ui.DisplayZone.zone,
    ): List<Component> = CatalogScreen(routes, now, zone).announcement(snapshots, support)

    /** Renders one short message of an update command: a mark in the tone's color, the text and its detail. */
    fun notice(notice: ChatNotice, routes: UpdateCatalogRoutes): List<Component> = kit(routes).notice(notice)

    /** Renders [notices] one after another, as a single reply. */
    fun notices(notices: List<ChatNotice>, routes: UpdateCatalogRoutes): List<Component> = notices.flatMap { notice(it, routes) }

    /**
     * Renders a card that asks to confirm a risky action, such as a rollback or a restart.
     *
     * @param lines what will happen, one sentence per line
     * @param command the command that confirms; a reader that cannot click is told to type it
     */
    fun confirmation(
        title: String,
        lines: List<String>,
        label: String,
        command: String,
        hint: String,
        routes: UpdateCatalogRoutes,
    ): List<Component> = kit(routes).confirmation(title, lines, label, command, hint)

    private fun kit(routes: UpdateCatalogRoutes) = ChatKit(ChatReader(routes.interactive, routes.ascii))
}
