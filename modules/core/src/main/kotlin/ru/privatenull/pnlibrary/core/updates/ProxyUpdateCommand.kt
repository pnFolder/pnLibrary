package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.core.runtime.registerRuntimeCommand
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.updates.UpdateActor
import java.util.UUID

/** Console-only update controls shared by both proxy platforms. */
class ProxyUpdateCommand(private val library: PnLibrary) {
    /** Registers the portable update command for this library runtime. */
    fun register(): CommandRegistration = library.registerRuntimeCommand(definition())

    /** Builds the portable `pnupdate` command definition. */
    fun definition(): CommandDefinition = command("pnupdate") {
        permission("pnlibrary.updates.console")
        executes { execute(it, "plan") }
        literal("plan") { executes { execute(it, "plan") } }
        literal("check") { executes { execute(it, "check") } }
        literal("list") {
            executes { showCatalog(it, false) }
            argument("page", ArgumentType.integer()) { executes { showCatalog(it, false) } }
        }
        literal("available") {
            executes { showCatalog(it, true) }
            argument("page", ArgumentType.integer()) { executes { showCatalog(it, true) } }
        }
        literal("history") {
            executes(::showHistory)
            argument("page", ArgumentType.integer()) { executes(::showHistory) }
        }
        literal("backups") {
            executes(::showBackups)
            argument("page", ArgumentType.integer()) { executes(::showBackups) }
        }
        literal("rollback") {
            argument("transaction", ArgumentType.string()) {
                executes { rollbackSaved(it, false) }
                literal("confirm") { executes { rollbackSaved(it, true) } }
                literal("configs") {
                    executes { rollbackSaved(it, false, true) }
                    literal("confirm") { executes { rollbackSaved(it, true, true) } }
                }
            }
        }
        literal("stage") {
            executes { execute(it, "stage") }
            argument("plan", ArgumentType.string()) { executes { execute(it, "stage") } }
        }
    }

    private fun execute(context: CommandContext, action: String) {
        if (!context.sender.isConsole) {
            context.sender.send(Component.text("pnLibrary update actions are console-only on proxy platforms."))
            return
        }
        when (action) {
            "check" -> library.updates.checkNow(actor(context)).whenComplete { snapshot, error ->
                context.sender.send(Component.text(error?.let { "Update check failed: ${it.message}" }
                    ?: "Update check complete: ${snapshot.state}, revision ${snapshot.revision}"))
            }
            "stage" -> {
                val current = library.updates.currentPlan().orElse(null)
                val requested = if (context.contains("plan")) runCatching { UUID.fromString(context.get<String>("plan")) }.getOrNull() else current?.id
                if (requested == null) context.sender.send(Component.text("No update plan is available or the plan ID is invalid."))
                else library.updates.stage(requested, actor(context)).whenComplete { snapshot, error ->
                    context.sender.send(Component.text(error?.let { "Update staging failed: ${it.message}" }
                        ?: "Update plan staged: ${snapshot.id}"))
                }
            }
            else -> {
                val current = library.updates.currentPlan().orElse(null)
                context.sender.send(Component.text(current?.let {
                    "Update plan ${it.id}, revision ${it.revision}: ${it.state}; changes=${it.plan?.changes?.size ?: 0}; blockers=${it.blockers.size}"
                } ?: "No update plan has been resolved."))
            }
        }
    }

    private fun showCatalog(context: CommandContext, updatesOnly: Boolean) {
        if (!context.sender.isConsole) {
            context.sender.send(Component.text("pnLibrary proxy catalog is console-only."))
            return
        }
        val page = if (context.contains("page")) context.get<Int>("page") else 1
        if (page < 1) { context.sender.send(Component.text("Page must be positive.")); return }
        UpdateCatalogView.catalog(library.updates.all().map { it.snapshot }, page, updatesOnly,
            if (updatesOnly) "/pnupdate available" else "/pnupdate list").forEach(context.sender::send)
    }

    private fun showHistory(context: CommandContext) {
        if (!context.sender.isConsole) {
            context.sender.send(Component.text("pnLibrary proxy history is console-only."))
            return
        }
        val page = if (context.contains("page")) context.get<Int>("page") else 1
        if (page < 1) { context.sender.send(Component.text("Page must be positive.")); return }
        library.updates.historyPage(page).whenComplete { entries, error ->
            if (error != null) context.sender.send(Component.text("Could not read update history."))
            else UpdateCatalogView.history(entries, page, "/pnupdate history").forEach(context.sender::send)
        }
    }

    private fun showBackups(context: CommandContext) {
        if (!context.sender.isConsole) { context.sender.send(Component.text("Rollback sets are console-only on proxies.")); return }
        val page = if (context.contains("page")) context.get<Int>("page") else 1
        if (page < 1) { context.sender.send(Component.text("Page must be positive.")); return }
        library.updates.backups().whenComplete { entries, error ->
            if (error != null) context.sender.send(Component.text("Could not read rollback sets."))
            else UpdateCatalogView.backups(entries, page, "/pnupdate backups").forEach(context.sender::send)
        }
    }

    private fun rollbackSaved(context: CommandContext, confirmed: Boolean, restoreConfigurations: Boolean = false) {
        if (!context.sender.isConsole) { context.sender.send(Component.text("Rollback is console-only on proxies.")); return }
        val id = runCatching { UUID.fromString(context.get<String>("transaction")) }.getOrNull()
        if (id == null) { context.sender.send(Component.text("Invalid transaction ID.")); return }
        if (!confirmed) {
            val option = if (restoreConfigurations) " configs" else ""
            context.sender.send(Component.text("Restore saved JARs${if (restoreConfigurations) " AND selected configuration files" else " without changing configurations"} with the offline updater: /pnupdate rollback $id$option confirm"))
            return
        }
        library.updates.rollback(id, restoreConfigurations, actor(context)).whenComplete { _, error ->
            context.sender.send(Component.text(if (error == null) "Rollback set $id prepared. A full restart is required." else "Rollback failed: ${error.message}"))
        }
    }

    private fun actor(context: CommandContext) = UpdateActor(UpdateActor.Kind.CONSOLE, context.sender.id, context.sender.name)
}
