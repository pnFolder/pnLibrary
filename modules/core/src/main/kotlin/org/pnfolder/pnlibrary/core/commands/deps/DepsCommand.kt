package org.pnfolder.pnlibrary.core.commands.deps

import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.downloads.DownloadState
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.lifecycle.dependencyDownloads

/**
 * `/pn deps`: plugins that other plugins need and the server does not have.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn deps` | what is missing, with the links, and the code of each group |
 * | `/pn deps install <code>` | downloads that group now, even when automatic download is off |
 * | `/pn deps auto on\|off` | whether missing plugins are downloaded without a command |
 *
 * The automatic switch is kept in the data folder and is the same one the game menu changes. Requires
 * `pnlibrary.deps`; the console always may use it.
 */
class DepsCommand(private val library: PnLibrary) {
    fun definition(): CommandDefinition = command("deps") {
        permission(PERMISSION)
        noPermissionMessage("Зависимости плагинов доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { status(it) }
        literal("install") {
            argument("code", ArgumentType.string()) {
                suggests { library.dependencyDownloads()?.missingGroups()?.map { it.code }.orEmpty() }
                executes { install(it, it.get<String>("code")) }
            }
        }
        literal("auto") {
            literal("on") { executes { switch(it, true) } }
            literal("off") { executes { switch(it, false) } }
        }
    }

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun say(context: CommandContext, text: String, color: TextColor = ChatPalette.TEXT) =
        context.sender.send(kit(context).text(text, color))

    private fun status(context: CommandContext) {
        val downloads = library.dependencyDownloads() ?: return say(context, "Загрузки зависимостей здесь недоступны.", ChatPalette.DANGER)
        say(context, "Автозагрузка недостающих плагинов: ${if (downloads.automatic()) "включена" else "выключена"}.", ChatPalette.ACCENT)
        val groups = downloads.missingGroups()
        if (groups.isEmpty()) return say(context, "Всё нужное установлено.", ChatPalette.LABEL)
        groups.forEach { group ->
            say(context, "${group.owner} просит: ${group.items.joinToString { "${it.plugin} ${it.minimum}+" }}")
            group.items.forEach { item -> (listOfNotNull(item.page) + item.sources).forEach { say(context, "  $it", ChatPalette.LABEL) } }
            say(context, if (group.installable) "  Установить: /pn deps install ${group.code}" else "  Скачайте по ссылкам и положите в plugins.", ChatPalette.SOFT)
        }
    }

    private fun install(context: CommandContext, code: String) {
        val downloads = library.dependencyDownloads() ?: return say(context, "Загрузки зависимостей здесь недоступны.", ChatPalette.DANGER)
        val stage = try {
            downloads.installGroup(code)
        } catch (error: IllegalArgumentException) {
            return say(context, error.message ?: "Установить нельзя.", ChatPalette.DANGER)
        } ?: return say(context, "Такого кода нет или всё уже скачано. Список: /pn deps", ChatPalette.DANGER)
        say(context, "Скачиваю…", ChatPalette.LABEL)
        stage.whenComplete { shots, error ->
            val failed = shots?.firstOrNull { it.state == DownloadState.FAILED }
            when {
                error != null -> say(context, "Не вышло: ${error.message}", ChatPalette.DANGER)
                failed != null -> say(context, "Не вышло: ${failed.message ?: "причина неизвестна"}", ChatPalette.DANGER)
                else -> say(context, "Готово. Перезапустите сервер, чтобы плагины загрузились.", ChatPalette.ACCENT)
            }
        }
    }

    private fun switch(context: CommandContext, value: Boolean) {
        val downloads = library.dependencyDownloads() ?: return say(context, "Загрузки зависимостей здесь недоступны.", ChatPalette.DANGER)
        downloads.setAutomatic(value)
        say(context, if (value) "Недостающие плагины теперь скачиваются сами." else "Автозагрузка выключена: ссылки и код будут в сообщении.", ChatPalette.ACCENT)
    }

    companion object {
        const val PERMISSION = "pnlibrary.deps"
    }
}
