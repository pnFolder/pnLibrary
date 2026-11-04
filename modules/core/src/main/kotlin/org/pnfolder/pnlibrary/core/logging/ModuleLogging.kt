package org.pnfolder.pnlibrary.core.logging

import net.kyori.adventure.text.format.NamedTextColor
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.MessageBox
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.core.ui.ConsoleStyle
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshotProvider

/** Adapts library logging, diagnostics capture, and lifecycle summaries to one platform. */
internal class ModuleLogging(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnership,
    private val diagnostics: PlatformSnapshotProvider,
    private val identity: PlatformIdentity,
    private val diagnosticLogs: DiagnosticLogBuffer? = null,
    private val journal: ((Any, LogLevel, String, Throwable?) -> Unit)? = null,
    private val capture: DiagnosticCapture = DiagnosticCapture(ownership, logging, diagnosticLogs, journal),
) : LoggingService {
    private data class Row(val status: String, val label: String, val detail: String, val error: Throwable?)
    private enum class MessageKind { GENERIC, ENABLED, DISABLED }

    override fun logger(owner: Any, name: String): PnLogger = object : PnLogger {
        override fun log(level: LogLevel, message: String, error: Throwable?, record: Boolean) =
            capture.write(owner, level, "[$name] $message", error, record)
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
            val card = Card()
            card.header()
            card.details()
            card.result()
            card.support()
        }

        /** The look of one startup card: colours, the product facts and the blocks printed in order. */
        private inner class Card {
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
            private val ownerMetadata = ownership.ownerMetadata(owner)
            val product = explicitName ?: ownerMetadata?.name ?: title.split(Regex("\\s+"), limit = 2).firstOrNull().orEmpty()

            private fun line(text: String) = logging.console(owner, text)

            fun header() {
                val stateLabel = when (kind) {
                    MessageKind.ENABLED -> "ПЛАГИН ВКЛЮЧЁН"
                    MessageKind.DISABLED -> "ПЛАГИН ВЫКЛЮЧЁН"
                    MessageKind.GENERIC -> "СООБЩЕНИЕ"
                }
                val environment = diagnostics.snapshot()
                val version = explicitVersion ?: ownerMetadata?.version ?: "неизвестна"
                val authors = ownerMetadata?.authors?.joinToString(", ").orEmpty().ifBlank { "неизвестны" }
                line("")
                line("$accent          ━━━━━━━━━━━ ${bold}PNFOLDER PLUGIN$accent ━━━━━━━━━━━")
                line("$stateColor /\\_/\\")
                line("$stateColor( ${face()} )     ${text}$bold$product $soft› ${accent}pnFolder")
                line("$stateColor > ^ <      $stateColor● $bold$stateLabel")
                line("")
                line("$soft            ┌ ${hint}Версия       $text$version")
                line("$soft            ├ ${hint}Автор        $text$authors")
                line("$soft            ├ ${hint}Ядро         $text${environment.name} ${environment.version}")
                line("$soft            ├ ${hint}Java         $text${environment.java.version}")
                line("$soft            └ ${hint}Библиотека   ${text}pnLibrary $soft• $hint${identity.id}")
                line("")
            }

            fun details() {
                val sectionTitle = when (kind) {
                    MessageKind.ENABLED -> "СОСТОЯНИЕ СИСТЕМЫ"
                    MessageKind.DISABLED -> "ЗАВЕРШЕНИЕ РАБОТЫ"
                    MessageKind.GENERIC -> "ПОДРОБНОСТИ"
                }
                line("$soft          ─────────── ${text}$bold$sectionTitle$soft ───────────")
                line("")
                val longestLabel = rows.maxOfOrNull { it.label.length } ?: 0
                rows.forEach { row ->
                    val label = row.label.padEnd(longestLabel)
                    line("$soft          ${symbolOf(row.status)} $text$label  ${colorOf(row.status)}$bold[ ${row.status} ]")
                    if (row.detail.isNotBlank()) line("$soft            └ $hint${row.detail}")
                    row.error?.let { writeCaptured(owner, LogLevel.ERROR, "$title — ${row.label}", it) }
                }
                line("")
                line("$soft          ───────────────────────────────────────────────")
            }

            fun result() {
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
                line("$stateColor          $resultSymbol $text$bold$resultText")
                line("$hint            $resultDetail")
                line("")
            }

            fun support() {
                line("$accent          ✦ ${bold}PNFOLDER SUPPORT $soft• ${text}НА СВЯЗИ")
                line("$text            Есть вопрос, ошибка, идея или просто нужна помощь?")
                line("$hint            Пишите по любому поводу — разберёмся, подскажем и поможем.")
                line("$hint            Нужен новый плагин? Обсудим бесплатную или заказную разработку.")
                line("$hint            Questions, bugs or ideas? Write to us any time: we are always glad to help.")
                line("$accent            Discord $soft› ${text}${ConsoleStyle.UNDERLINE}${PnLibraryIdentity.SUPPORT_URL}")
                line("$soft          ───────────────────────────────────────────────")
                line("")
            }

            private fun colorOf(status: String) = when (status) {
                "OK" -> success
                "WARN" -> warning
                "FAIL" -> danger
                else -> hint
            }

            private fun symbolOf(status: String) = when (status) {
                "OK" -> "◆"
                "WARN" -> "▲"
                "FAIL" -> "✕"
                else -> "◇"
            }
        }

        private fun face() = when {
            kind == MessageKind.DISABLED -> "-.-"
            rows.any { it.status == "FAIL" } -> "x.x"
            rows.any { it.status == "WARN" } -> "o.o"
            else -> "^.^"
        }
    }

    private fun writeCaptured(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        capture.log(owner, level, message, error)
    }

}
