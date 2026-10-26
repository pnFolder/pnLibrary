package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.UUID

/**
 * Console-only update controls shared by both proxy platforms, mounted as `/pn update` by [ProxyControlCommand].
 */
class ProxyUpdateCommand(private val library: PnLibrary) {
    private val catalog = UpdateCatalogCommands(
        library = library,
        routes = UpdateCatalogRoutes("/pn update"),
        guard = { context ->
            context.sender.isConsole.also { allowed ->
                if (!allowed) context.sender.send(Component.text("На прокси каталог обновлений доступен только из консоли."))
            }
        },
    )

    /** Builds the portable update command definition; it is mounted under `/pn` as `update`. */
    fun definition(): CommandDefinition = command("update") {
        permission("pnlibrary.updates.console")
        executes { execute(it, "plan") }
        literal("plan") { executes { execute(it, "plan") } }
        literal("check") { executes { execute(it, "check") } }
        catalog.install { name, configure -> literal(name, configure) }
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
            context.sender.send(Component.text("На прокси управлять обновлениями можно только из консоли."))
            return
        }
        when (action) {
            "check" -> library.updates.checkNow(actor(context)).whenComplete { snapshot, error ->
                context.sender.send(Component.text(error?.let { "Не удалось проверить обновления: ${it.message}" }
                    ?: "Проверка завершена: ${snapshot.state}, ревизия ${snapshot.revision}"))
            }
            "stage" -> {
                val current = library.updates.currentPlan().orElse(null)
                val requested = if (context.contains("plan")) runCatchingNonFatal { UUID.fromString(context.get<String>("plan")) }.getOrNull() else current?.id
                if (requested == null) context.sender.send(Component.text("Нет плана обновления, или его ID указан неверно."))
                else library.updates.stage(requested, actor(context)).whenComplete { snapshot, error ->
                    context.sender.send(Component.text(error?.let { "Не удалось подготовить обновление: ${it.message}" }
                        ?: "План обновления подготовлен: ${snapshot.id}"))
                }
            }
            else -> {
                val current = library.updates.currentPlan().orElse(null)
                context.sender.send(Component.text(current?.let {
                    "План ${it.id}, ревизия ${it.revision}: ${it.state}; изменений ${it.plan?.changes?.size ?: 0}, блокеров ${it.blockers.size}"
                } ?: "План обновления ещё не составлен."))
            }
        }
    }

    private fun rollbackSaved(context: CommandContext, confirmed: Boolean, restoreConfigurations: Boolean = false) {
        if (!context.sender.isConsole) { context.sender.send(Component.text("На прокси откат доступен только из консоли.")); return }
        val id = runCatchingNonFatal { UUID.fromString(context.get<String>("transaction")) }.getOrNull()
        if (id == null) { context.sender.send(Component.text("Неверный ID набора.")); return }
        if (!confirmed) {
            val option = if (restoreConfigurations) " configs" else ""
            val restored = if (restoreConfigurations) " вместе с выбранными файлами конфигурации" else " без изменения конфигураций"
            context.sender.send(Component.text("Вернуть сохранённые JAR$restored: /pn update rollback $id$option confirm"))
            return
        }
        library.updates.rollback(id, restoreConfigurations, actor(context)).whenComplete { _, error ->
            val message = if (error == null) {
                "Набор $id подготовлен. Полностью перезапустите прокси."
            } else {
                "Не удалось откатить: ${error.message}"
            }
            context.sender.send(Component.text(message))
        }
    }

    private fun actor(context: CommandContext) = UpdateActor(UpdateActor.Kind.CONSOLE, context.sender.id, context.sender.name)
}
