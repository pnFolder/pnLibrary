package ru.privatenull.pnlibrary.core.logging

import net.kyori.adventure.text.format.NamedTextColor
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.core.ui.ConsoleStyle
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshotProvider

/** Adapts library logging, diagnostics capture, and lifecycle summaries to one platform. */
internal class PlatformLoggingService(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnership,
    private val diagnostics: PlatformSnapshotProvider,
    private val identity: PlatformIdentity,
    private val diagnosticLogs: DiagnosticLogBuffer? = null,
) : LoggingService {
    private data class Row(val status: String, val label: String, val detail: String, val error: Throwable?)
    private enum class MessageKind { GENERIC, ENABLED, DISABLED }

    override fun logger(owner: Any, name: String): PnLogger = object : PnLogger {
        private fun write(level: LogLevel, message: String, error: Throwable? = null) {
            val formatted = "[$name] $message"
            writeCaptured(owner, level, formatted, error)
        }
        override fun info(message: String) = write(LogLevel.INFO, message)
        override fun success(message: String) = write(LogLevel.SUCCESS, message)
        override fun warning(message: String) = write(LogLevel.WARNING, message)
        override fun error(message: String, error: Throwable?) = write(LogLevel.ERROR, message, error)
    }

    override fun box(owner: Any, title: String): MessageBox =
        MBox(owner, title, MessageKind.GENERIC, title)
    override fun shutdownBox(owner: Any, title: String): MessageBox =
        MBox(owner, title, MessageKind.DISABLED)
    override fun box(owner: Any, name: String, version: String): MessageBox =
        MBox(owner, "$name $version", MessageKind.ENABLED, name, version)
    override fun shutdownBox(owner: Any, name: String, version: String): MessageBox =
        MBox(owner, "$name $version", MessageKind.DISABLED, name, version)

    private inner class MBox(
        private val owner: Any,
        private val title: String,
        private val kind: MessageKind,
        private val explicitName: String? = null,
        private val explicitVersion: String? = null,
    ) : MessageBox {
        private val rows = mutableListOf<Row>()
        private var shown = false
        override fun ok(label: String, detail: String) = add(Row("OK", label, detail, null))
        override fun warn(label: String, detail: String) = add(Row("WARN", label, detail, null))
        override fun skip(label: String, detail: String) = add(Row("SKIP", label, detail, null))
        override fun fail(label: String, detail: String, error: Throwable?) = add(Row("FAIL", label, detail, error))

        private fun add(row: Row) = apply {
            check(!shown) { "MessageBox has already been shown" }
            require(row.label.isNotBlank()) { "MessageBox label must not be blank" }
            rows += row
        }

        override fun show() {
            check(!shown) { "MessageBox has already been shown" }
            shown = true
            // The startup card keeps its own established console look: yellow brand, gray labels, dark rules.
            val accent = ConsoleStyle.color(NamedTextColor.YELLOW)
            val success = ConsoleStyle.color(NamedTextColor.GREEN)
            val warning = ConsoleStyle.color(NamedTextColor.GOLD)
            val danger = ConsoleStyle.color(NamedTextColor.RED)
            val text = ConsoleStyle.color(NamedTextColor.WHITE)
            val hint = ConsoleStyle.color(NamedTextColor.GRAY)
            val soft = ConsoleStyle.color(NamedTextColor.DARK_GRAY)
            val bold = ConsoleStyle.BOLD
            val stateColor = when (kind) {
                MessageKind.ENABLED -> success
                MessageKind.DISABLED -> danger
                MessageKind.GENERIC -> accent
            }
            val stateLabel = when (kind) {
                MessageKind.ENABLED -> "ПЛАГИН ВКЛЮЧЁН"
                MessageKind.DISABLED -> "ПЛАГИН ВЫКЛЮЧЁН"
                MessageKind.GENERIC -> "СООБЩЕНИЕ"
            }
            val longestLabel = rows.maxOfOrNull { it.label.length } ?: 0
            fun statusColor(status: String) = when (status) {
                "OK" -> success
                "WARN" -> warning
                "FAIL" -> danger
                else -> hint
            }
            val titleParts = title.split(Regex("\\s+"), limit = 2)
            val ownerMetadata = ownership.ownerMetadata(owner)
            val environment = diagnostics.snapshot()
            val product = explicitName ?: ownerMetadata?.name ?: titleParts.firstOrNull().orEmpty()
            val version = explicitVersion ?: ownerMetadata?.version ?: "неизвестна"
            val authors = ownerMetadata?.authors?.joinToString(", ").orEmpty().ifBlank { "неизвестны" }
            val engineName = environment.name
            val engineVersion = environment.version
            val javaVersion = environment.java.version

            logging.console(owner, "")
            logging.console(owner, "$accent          ━━━━━━━━━━━ ${bold}PNFOLDER PLUGIN$accent ━━━━━━━━━━━")
            logging.console(owner, "$stateColor /\\_/\\")
            logging.console(owner, "$stateColor( ${face()} )     ${text}$bold$product $soft› ${accent}pnFolder")
            logging.console(owner, "$stateColor > ^ <      $stateColor● $bold$stateLabel")
            logging.console(owner, "")
            logging.console(owner, "$soft            ┌ ${hint}Версия       $text$version")
            logging.console(owner, "$soft            ├ ${hint}Автор        $text$authors")
            logging.console(owner, "$soft            ├ ${hint}Ядро         $text$engineName $engineVersion")
            logging.console(owner, "$soft            ├ ${hint}Java         $text$javaVersion")
            logging.console(owner, "$soft            └ ${hint}Библиотека   ${text}pnLibrary $soft• $hint${identity.id}")
            logging.console(owner, "")
            val sectionTitle = when (kind) {
                MessageKind.ENABLED -> "СОСТОЯНИЕ СИСТЕМЫ"
                MessageKind.DISABLED -> "ЗАВЕРШЕНИЕ РАБОТЫ"
                MessageKind.GENERIC -> "ПОДРОБНОСТИ"
            }
            logging.console(owner, "$soft          ─────────── ${text}$bold$sectionTitle$soft ───────────")
            logging.console(owner, "")
            rows.forEach { row ->
                val color = statusColor(row.status)
                val label = row.label.padEnd(longestLabel)
                val symbol = when (row.status) {
                    "OK" -> "◆"
                    "WARN" -> "▲"
                    "FAIL" -> "✕"
                    else -> "◇"
                }
                logging.console(owner, "$soft          $symbol $text$label  $color$bold[ ${row.status} ]")
                if (row.detail.isNotBlank()) {
                    logging.console(owner, "$soft            └ $hint${row.detail}")
                }
                row.error?.let {
                    val message = "$title — ${row.label}"
                    writeCaptured(owner, LogLevel.ERROR, message, it)
                }
            }
            logging.console(owner, "")
            logging.console(owner, "$soft          ───────────────────────────────────────────────")
            val resultText = when (kind) {
                MessageKind.ENABLED -> "$product успешно включён"
                MessageKind.DISABLED -> "$product корректно выключен"
                MessageKind.GENERIC -> "$product — готово"
            }
            val resultDetail = when (kind) {
                MessageKind.ENABLED -> "Все основные системы готовы. Плагин работает в штатном режиме."
                MessageKind.DISABLED -> "Все зарегистрированные ресурсы освобождены. До следующего запуска."
                MessageKind.GENERIC -> "Все строки сообщения выведены одним блоком."
            }
            val resultSymbol = when (kind) {
                MessageKind.ENABLED -> "✓"
                MessageKind.DISABLED -> "■"
                MessageKind.GENERIC -> "◆"
            }
            logging.console(owner, "$stateColor          $resultSymbol $text$bold$resultText")
            logging.console(owner, "$hint            $resultDetail")
            logging.console(owner, "")
            logging.console(owner, "$accent          ✦ ${bold}PNFOLDER SUPPORT $soft• ${text}НА СВЯЗИ")
            logging.console(owner, "$text            Есть вопрос, ошибка, идея или просто нужна помощь?")
            logging.console(owner, "$hint            Пишите по любому поводу — разберёмся, подскажем и поможем.")
            logging.console(owner, "$hint            Нужен новый плагин? Обсудим бесплатную или заказную разработку.")
            logging.console(owner, "$accent            Discord $soft› ${text}${ConsoleStyle.UNDERLINE}${PnLibraryBrand.SUPPORT_URL}")
            logging.console(owner, "$soft          ───────────────────────────────────────────────")
            logging.console(owner, "")
        }

        private fun face() = when {
            kind == MessageKind.DISABLED -> "-.-"
            rows.any { it.status == "FAIL" } -> "x.x"
            rows.any { it.status == "WARN" } -> "o.o"
            else -> "^.^"
        }
    }

    private fun writeCaptured(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val capture = diagnosticLogs?.record(ownership, owner, level, message, error)
        when {
            capture == null || capture.emitOriginal -> logging.log(owner, level, message, error)
            capture.summary != null -> logging.log(owner, level, capture.summary, null)
        }
    }

}
