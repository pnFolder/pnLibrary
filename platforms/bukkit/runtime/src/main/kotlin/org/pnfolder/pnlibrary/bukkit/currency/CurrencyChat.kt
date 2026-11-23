package org.pnfolder.pnlibrary.bukkit.currency

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.ChatColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.currency.Currency
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings
import org.pnfolder.pnlibrary.api.currency.CurrencyResult
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun settings(currency: Currency) = currency.extension(CurrencyCommandSettings::class.java) ?: DEFAULT_MESSAGES

internal fun kit(sender: BukkitCommandSender) = ChatKit(ChatReader.of(sender))

/**
 * A configured template of [currency] after the notice mark of [tone]. The template keeps its own legacy
 * colors; the mark is a sibling, so uncolored template text does not take the tone's color.
 */
internal fun configured(
    sender: BukkitCommandSender,
    currency: Currency,
    tone: ChatTone,
    template: String,
    values: Map<String, String> = emptyMap(),
) {
    val prefix = settings(currency).prefix.replace("{currency}", currency.descriptor.displayName)
    var body = template
    values.forEach { (key, value) -> body = body.replace("{$key}", value) }
    val message = LEGACY.deserialize(ChatColor.translateAlternateColorCodes('&', prefix + body))
    sender.send(Component.empty().append(kit(sender).mark(tone)).append(message))
}

internal fun notify(sender: BukkitCommandSender, tone: ChatTone, text: String, detail: String? = null): Boolean {
    kit(sender).notice(ChatNotice(tone, text, detail)).forEach(sender::send)
    return true
}

internal fun error(sender: BukkitCommandSender, text: String, detail: String? = null): Boolean =
    notify(sender, ChatTone.FAILURE, text, detail)

internal fun showResult(sender: BukkitCommandSender, currency: Currency, result: CurrencyResult, amount: BigDecimal) {
    val settings = settings(currency)
    if (result.isSuccess) {
        configured(sender, currency, ChatTone.SUCCESS, settings.success, mapOf("amount" to currency.format(amount)))
    } else {
        val error = result.message ?: result.reason?.name ?: result.status.name
        configured(sender, currency, ChatTone.FAILURE, settings.failure, mapOf("error" to error))
    }
}

internal fun typeLabel(type: CurrencyTransactionType): String = when (type) {
    CurrencyTransactionType.CREDIT -> "начисление"
    CurrencyTransactionType.DEBIT -> "списание"
    CurrencyTransactionType.TRANSFER -> "перевод"
    CurrencyTransactionType.SET_BALANCE -> "установка баланса"
    CurrencyTransactionType.RESET -> "сброс"
}

internal fun usage(sender: BukkitCommandSender, currency: Currency): Boolean =
    notify(sender, ChatTone.WARNING, "Укажите действие.", "/pn money ${currency.key} <balance|add|take|set|reset|pay|history>")

internal fun help(sender: BukkitCommandSender): Boolean {
    val kit = kit(sender)
    val hints = kit.commandHints(
        "/pn money list" to "список валют",
        "/pn money <валюта> balance [игрок]" to "баланс",
        "/pn money <валюта> pay <игрок> <сумма>" to "перевести игроку",
        "/pn money <валюта> history [игрок]" to "последние операции",
        "/pn money <валюта> add <игрок> <сумма>" to "начислить",
        "/pn money <валюта> take <игрок> <сумма>" to "списать",
        "/pn money <валюта> set <игрок> <сумма>" to "установить баланс",
        "/pn money <валюта> reset <игрок>" to "обнулить баланс",
        "/pn money confirm <код>" to "подтвердить операцию из консоли",
        "/pn money cancel <код>" to "отменить операцию из консоли",
    )
    val intro = kit.note(if (kit.reader.interactive) "Нажмите на команду, чтобы вставить её в чат." else "Доступные действия зависят от прав.")
    kit.framed(listOf(kit.header("Валюты", kit.text("помощь", ChatPalette.LABEL)), intro, kit.blank()) + hints).forEach(sender::send)
    return true
}

internal val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
internal val DEFAULT_MESSAGES = CurrencyCommandSettings()
internal val LEGACY = LegacyComponentSerializer.legacySection()
