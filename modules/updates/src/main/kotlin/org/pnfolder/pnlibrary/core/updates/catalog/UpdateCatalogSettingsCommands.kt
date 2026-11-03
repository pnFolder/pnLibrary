package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.updates.engine.FreezeDuration
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletionStage

/** The red chat line the catalog commands use for a refusal. */
internal fun catalogError(text: String): Component = Component.text(text, ChatPalette.DANGER)

/**
 * The `set <plugin> channel|auto|hold` commands of the catalog: each changes one setting of a component and then
 * shows the refreshed settings card with a confirmation.
 *
 * @param ready returns whether the command may run for this sender; it reports the reason itself
 * @param routesFor the routes adapted to the sender
 */
internal class UpdateCatalogSettingsCommands(
    private val library: PnLibrary,
    private val ready: (CommandContext) -> Boolean,
    private val routesFor: (CommandContext) -> UpdateCatalogRoutes,
    private val deliver: (Runnable) -> Unit,
    private val onSaved: (CommandContext, UpdateCatalogCommands.SettingSaved) -> Unit,
) {
    /** The channels an administrator may choose; only `dev` is published for now, so the others stay switched off. */
    fun available(): List<UpdateChannel> = if (ONLY_DEV) listOf(UpdateChannel.DEV) else UpdateCatalogView.CHANNELS

    /** Sets the release channel, or removes the administrator's choice with `default` so the plugin decides again. */
    fun setChannel(context: CommandContext) {
        val value = context.get<String>("value")
        if (value.equals(DEFAULT_CHANNEL, ignoreCase = true)) {
            change(context, "Канал", { it.channel.name.lowercase(Locale.ROOT) }, "как задал плагин") { product, actor ->
                library.updates.setChannel(product, null, actor)
            }
            return
        }
        val channel = UpdateCatalogView.CHANNELS.firstOrNull { it.name.equals(value, ignoreCase = true) }
        if (channel == null) {
            context.sender.send(catalogError("Неизвестный канал $value. Доступны: ${available().joinToString { it.name.lowercase(Locale.ROOT) }}."))
            return
        }
        if (channel !in available()) {
            context.sender.send(catalogError("Сейчас работает только канал dev, остальные пока не выпускаются. Выберите dev или default."))
            return
        }
        change(context, "Канал", { it.channel.name.lowercase(Locale.ROOT) }, channel.name.lowercase(Locale.ROOT)) { product,
            actor -> library.updates.setChannel(product, channel, actor) }
    }

    fun setAutomatic(context: CommandContext) {
        val enabled = switch(context) ?: return
        change(context, "Автозагрузка", { if (it.automaticDownload) "включена" else "выключена" }, if (enabled) "включена" else "выключена") { product, actor ->
            library.updates.setAutomaticDownload(product, enabled, actor)
        }
    }

    /** Pauses updates for a period such as `3d` (`on` means seven days), or lifts the pause with `off`. */
    fun setHeld(context: CommandContext) {
        val value = context.get<String>("value").lowercase(Locale.ROOT)
        val duration = when (value) {
            in OFF -> null
            in ON -> DEFAULT_HOLD
            else -> runCatchingNonFatal { FreezeDuration.parse(value) }.getOrElse {
                context.sender.send(catalogError("Пауза — от 1m до 7d (например 3d) или off, получено: $value."))
                return
            }
        }
        change(context, "Пауза", { if (it.state == UpdateState.FROZEN) "на паузе" else "нет" },
            if (duration == null) "нет" else RussianText.period(duration)) { product, actor -> library.updates.setHeld(product, duration, actor) }
    }

    private fun switch(context: CommandContext): Boolean? {
        val value = context.get<String>("value")
        val parsed = when (value.lowercase(Locale.ROOT)) {
            in ON -> true
            in OFF -> false
            else -> null
        }
        if (parsed == null) context.sender.send(catalogError("Укажите on или off, получено: $value."))
        return parsed
    }

    /** Applies a setting, then confirms it below the refreshed settings card, or shows why it was refused. */
    private fun change(context: CommandContext, setting: String, current: (UpdateSnapshot) -> String, value: String, apply: (String,
        UpdateActor) -> CompletionStage<UpdateSnapshot>) {
        if (!ready(context)) return
        val product = context.get<String>("plugin")
        val sender = context.sender
        val before = library.updates.get(product)?.snapshot?.let(current) ?: value
        val actor = UpdateActor(if (sender.isConsole) UpdateActor.Kind.CONSOLE else UpdateActor.Kind.PLAYER, sender.id, sender.name)
        apply(product, actor).whenComplete { snapshot, failure ->
            deliver(Runnable {
                if (failure != null) {
                    val cause = generateSequence(failure) { it.cause }.last()
                    sender.send(catalogError(cause.message ?: "Не удалось изменить настройку."))
                } else {
                    val routes = routesFor(context)
                    val arrow = if (routes.ascii) "->" else "→"
                    val card = UpdateCatalogView.settings(snapshot, routes, library.updates.heldUntil(snapshot.product))
                    (card + UpdateCatalogView.notice(ChatNotice(ChatTone.SUCCESS, "Сохранено. $setting: $before $arrow $value."), routes)).forEach(sender::send)
                    onSaved(context, UpdateCatalogCommands.SettingSaved(snapshot.product, setting, before, value))
                }
            })
        }
    }

    companion object {
        val SWITCH_SUGGESTIONS = listOf("on", "off")
        const val DEFAULT_CHANNEL = "default"
        private val ON = setOf("on", "true", "yes", "1", "вкл")
        private val OFF = setOf("off", "false", "no", "0", "выкл")
        private val DEFAULT_HOLD: Duration = Duration.ofDays(7)

        /** Whether only the `dev` channel can be chosen; set to `false` when more channels are published. */
        private const val ONLY_DEV = true
    }
}
