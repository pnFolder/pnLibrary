package ru.privatenull.pnlibrary.core.logging

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.spi.platform.PlatformDiagnostics
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership

/** Adapts library logging, diagnostics capture, and lifecycle summaries to one platform. */
internal class PlatformLoggingService(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnership,
    private val diagnostics: PlatformDiagnostics,
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

    internal fun showUpdateNotice(owner: Any, product: String, current: String, latest: String, channel: String,
        minimumJava: Int, currentJava: Int, url: String) {
        showUpdate(owner, product, current, latest, channel, minimumJava, currentJava, url, downloaded = true)
    }

    internal fun showUpdateAvailableNotice(owner: Any, product: String, current: String, latest: String, channel: String,
        minimumJava: Int, currentJava: Int, url: String) {
        showUpdate(owner, product, current, latest, channel, minimumJava, currentJava, url, downloaded = false)
    }

    private fun showUpdate(owner: Any, product: String, current: String, latest: String, channel: String,
        minimumJava: Int, currentJava: Int, url: String, downloaded: Boolean) {
        val notice = UpdateNotice(
            product = product,
            currentVersion = current,
            latestVersion = latest,
            channel = channel,
            minimumJava = minimumJava,
            currentJava = currentJava,
            downloadUrl = url,
            downloaded = downloaded,
        )
        UpdateNoticeRenderer.render(notice).forEach { line -> logging.console(owner, line) }
    }

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
            val accent = "§e"
            val success = "§a"
            val warning = "§6"
            val danger = "§c"
            val white = "§f"
            val gray = "§7"
            val dark = "§8"
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
                else -> gray
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
            logging.console(owner, "$accent          ━━━━━━━━━━━ §lPNFOLDER PLUGIN$accent ━━━━━━━━━━━")
            logging.console(owner, "$stateColor /\\_/\\")
            logging.console(owner, "$stateColor( ${face()} )     ${white}§l$product $dark› ${accent}pnFolder")
            logging.console(owner, "$stateColor > ^ <      $stateColor● §l$stateLabel")
            logging.console(owner, "")
            logging.console(owner, "$dark            ┌ ${gray}Версия       $white$version")
            logging.console(owner, "$dark            ├ ${gray}Автор        $white$authors")
            logging.console(owner, "$dark            ├ ${gray}Ядро         $white$engineName $engineVersion")
            logging.console(owner, "$dark            ├ ${gray}Java         $white$javaVersion")
            logging.console(owner, "$dark            └ ${gray}Библиотека   ${white}pnLibrary $dark• $gray${identity.id}")
            logging.console(owner, "")
            val sectionTitle = when (kind) {
                MessageKind.ENABLED -> "СОСТОЯНИЕ СИСТЕМЫ"
                MessageKind.DISABLED -> "ЗАВЕРШЕНИЕ РАБОТЫ"
                MessageKind.GENERIC -> "ПОДРОБНОСТИ"
            }
            logging.console(owner, "$dark          ─────────── ${white}§l$sectionTitle$dark ───────────")
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
                logging.console(owner, "$dark          $symbol $white$label  $color§l[ ${row.status} ]")
                if (row.detail.isNotBlank()) {
                    logging.console(owner, "$dark            └ $gray${row.detail}")
                }
                row.error?.let {
                    val message = "$title — ${row.label}"
                    writeCaptured(owner, LogLevel.ERROR, message, it)
                }
            }
            logging.console(owner, "")
            logging.console(owner, "$dark          ───────────────────────────────────────────────")
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
            logging.console(owner, "$stateColor          $resultSymbol $white§l$resultText")
            logging.console(owner, "$gray            $resultDetail")
            logging.console(owner, "")
            logging.console(owner, "$accent          ✦ §lPNFOLDER SUPPORT $dark• ${white}НА СВЯЗИ")
            logging.console(owner, "$white            Есть вопрос, ошибка, идея или просто нужна помощь?")
            logging.console(owner, "$gray            Пишите по любому поводу — разберёмся, подскажем и поможем.")
            logging.console(owner, "$gray            Нужен новый плагин? Обсудим бесплатную или заказную разработку.")
            logging.console(owner, "$accent            Discord $dark› ${white}§n${PnLibraryBrand.SUPPORT_URL}")
            logging.console(owner, "$dark          ───────────────────────────────────────────────")
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
