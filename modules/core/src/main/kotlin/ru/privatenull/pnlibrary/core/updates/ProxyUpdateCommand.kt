package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.runtime.PnLibrary

/** Console-only update controls shared by both proxy platforms. */
class ProxyUpdateCommand(private val library: PnLibrary) {
    fun definition(): CommandDefinition = command("pnupdate") {
        permission("pnlibrary.updates.console")
        executes { execute(it, "plan") }
        literal("plan") { executes { execute(it, "plan") } }
        literal("check") { executes { execute(it, "check") } }
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
            "check" -> library.updates.checkNow().whenComplete { snapshot, error ->
                context.sender.send(Component.text(error?.let { "Update check failed: ${it.message}" }
                    ?: "Update check complete: ${snapshot.state}, revision ${snapshot.revision}"))
            }
            "stage" -> {
                val current = library.updates.currentPlan().orElse(null)
                if (current == null) context.sender.send(Component.text("No update plan is available."))
                else library.updates.stage(current.id).whenComplete { snapshot, error ->
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
}
