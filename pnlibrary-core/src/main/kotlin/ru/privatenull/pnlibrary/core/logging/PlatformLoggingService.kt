package ru.privatenull.pnlibrary.core.logging




import ru.privatenull.pnlibrary.api.diagnostics.*
import ru.privatenull.pnlibrary.api.logging.*
import ru.privatenull.pnlibrary.api.metrics.*
import ru.privatenull.pnlibrary.api.platform.*
import ru.privatenull.pnlibrary.api.runtime.*
import ru.privatenull.pnlibrary.api.tasks.*
import ru.privatenull.pnlibrary.api.updates.*

internal class PlatformLoggingService(private val platform: PlatformAdapter) : LoggingService {
    private data class Row(val status: String, val label: String, val detail: String, val error: Throwable?)

    override fun logger(owner: Any, name: String): PnLogger = object : PnLogger {
        private fun write(level: LogLevel, message: String, error: Throwable? = null) =
            platform.log(owner, level, "[$name] $message", error)
        override fun info(message: String) = write(LogLevel.INFO, message)
        override fun success(message: String) = write(LogLevel.SUCCESS, message)
        override fun warning(message: String) = write(LogLevel.WARNING, message)
        override fun error(message: String, error: Throwable?) = write(LogLevel.ERROR, message, error)
    }

    override fun box(owner: Any, title: String): MessageBox = MBox(owner, title, false)
    override fun shutdownBox(owner: Any, title: String): MessageBox = MBox(owner, title, true)

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
        val yellow = "§e"
        val green = "§a"
        val white = "§f"
        val gray = "§7"
        val dark = "§8"
        val channelName = channel.uppercase()
        val channelDescription = when (channel.lowercase()) {
            "stable" -> "стабильный канал"
            "beta" -> "бета-канал"
            "alpha" -> "альфа-канал"
            else -> "выбранный канал"
        }
        platform.console(owner, "")
        platform.console(owner, "$yellow          ━━━━━━━━━━━ §lНОВОЕ ОБНОВЛЕНИЕ$yellow ━━━━━━━━━━━")
        platform.console(owner, "$yellow /\\_/\\")
        platform.console(owner, "$yellow( ^o^ )     $white§l$product $dark› ${yellow}pnFolder")
        platform.console(owner, "$yellow > ^ <      ${gray}${if (downloaded) "Новая версия загружена и проверена" else "Доступна новая версия"}")
        platform.console(owner, "")
        platform.console(owner, "$dark            ┌ ${gray}Установлена: $white$current")
        platform.console(owner, "$dark            ├ ${gray}${if (downloaded) "Загружена" else "Доступна"}: $yellow§l$latest")
        platform.console(owner, "$dark            ├ ${gray}Канал: $white[ $channelName ] $dark• $gray$channelDescription")
        platform.console(owner, "$dark            ├ ${gray}Java: $white$currentJava $dark• ${gray}требуется $white$minimumJava+")
        platform.console(owner, "$dark            └ ${gray}Статус: ${if (downloaded) "$green§l✓ SHA-256 и JAR подтверждены" else "$yellow§lожидает ручной загрузки"}")
        platform.console(owner, "")
        if (downloaded) {
            platform.console(owner, "$green          ✓ $white§lОбновление подготовлено")
            platform.console(owner, "$gray            Оно установится автоматически после полного перезапуска сервера.")
        } else {
            platform.console(owner, "$yellow          ◆ $white§lАвтоматическая загрузка отключена")
            platform.console(owner, "$gray            Уведомления и ручное обновление продолжают работать.")
        }
        platform.console(owner, "$dark            $url")
        platform.console(owner, "")
        platform.console(owner, "$dark          ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        platform.console(owner, "")
    }

    private inner class MBox(private val owner: Any, private val title: String, private val shutdown: Boolean) : MessageBox {
        private val rows = mutableListOf<Row>()
        override fun ok(label: String, detail: String) = apply { rows += Row("OK", label, detail, null) }
        override fun warn(label: String, detail: String) = apply { rows += Row("WARN", label, detail, null) }
        override fun skip(label: String, detail: String) = apply { rows += Row("SKIP", label, detail, null) }
        override fun fail(label: String, detail: String, error: Throwable?) = apply { rows += Row("FAIL", label, detail, error) }
        override fun show() {
            val accent = "§e"
            val success = "§a"
            val warning = "§6"
            val danger = "§c"
            val white = "§f"
            val gray = "§7"
            val dark = "§8"
            val stateColor = if (shutdown) danger else success
            val stateLabel = if (shutdown) "ПЛАГИН ВЫКЛЮЧЕН" else "ПЛАГИН ВКЛЮЧЁН"
            val longestLabel = rows.maxOfOrNull { it.label.length } ?: 0
            fun statusColor(status: String) = when (status) {
                "OK" -> success
                "WARN" -> warning
                "FAIL" -> danger
                else -> gray
            }
            val titleParts = title.split(Regex("\\s+"), limit = 2)
            val ownerInfo = platform.ownerDetails(owner)
            val platformInfo = platform.details()
            val product = ownerInfo["name"] ?: titleParts.firstOrNull().orEmpty()
            val version = ownerInfo["version"] ?: titleParts.getOrNull(1).orEmpty().ifBlank { "неизвестна" }
            val authors = ownerInfo["authors"] ?: "pnFolder"
            val engineName = (platformInfo["serverName"] ?: platformInfo["proxyName"]
                ?: platformInfo["velocityName"] ?: platform.id).toString()
            val engineVersion = (platformInfo["bukkitVersion"] ?: platformInfo["proxyVersion"]
                ?: platformInfo["velocityVersion"] ?: "неизвестна").toString()
            val javaVersion = System.getProperty("java.version", "неизвестна")

            platform.console(owner, "")
            platform.console(owner, "$accent          ━━━━━━━━━━━ §lPNFOLDER PLUGIN$accent ━━━━━━━━━━━")
            platform.console(owner, "$stateColor /\\_/\\")
            platform.console(owner, "$stateColor( ${face()} )     ${white}§l$product $dark› ${accent}pnFolder")
            platform.console(owner, "$stateColor > ^ <      $stateColor● §l$stateLabel")
            platform.console(owner, "")
            platform.console(owner, "$dark            ┌ ${gray}Версия       $white$version")
            platform.console(owner, "$dark            ├ ${gray}Автор        $white$authors")
            platform.console(owner, "$dark            ├ ${gray}Ядро         $white$engineName $engineVersion")
            platform.console(owner, "$dark            ├ ${gray}Java         $white$javaVersion")
            platform.console(owner, "$dark            └ ${gray}Библиотека   ${white}pnLibrary $dark• $gray${platform.id}")
            platform.console(owner, "")
            val sectionTitle = if (shutdown) "ЗАВЕРШЕНИЕ РАБОТЫ" else "СОСТОЯНИЕ СИСТЕМЫ"
            platform.console(owner, "$dark          ─────────── ${white}§l$sectionTitle$dark ───────────")
            platform.console(owner, "")
            rows.forEach { row ->
                val color = statusColor(row.status)
                val label = row.label.padEnd(longestLabel)
                val symbol = when (row.status) { "OK" -> "◆"; "WARN" -> "▲"; "FAIL" -> "✕"; else -> "◇" }
                platform.console(owner, "$dark          $symbol $white$label  $color§l[ ${row.status} ]")
                if (row.detail.isNotBlank()) {
                    platform.console(owner, "$dark            └ $gray${row.detail}")
                }
                row.error?.let { platform.log(owner, LogLevel.ERROR, "$title — ${row.label}", it) }
            }
            platform.console(owner, "")
            platform.console(owner, "$dark          ───────────────────────────────────────────────")
            val resultText = if (shutdown) "$product корректно выключен" else "$product успешно включён"
            val resultDetail = if (shutdown) {
                "Все зарегистрированные ресурсы освобождены. До следующего запуска."
            } else {
                "Все основные системы готовы. Плагин работает в штатном режиме."
            }
            platform.console(owner, "$stateColor          ${if (shutdown) "■" else "✓"} $white§l$resultText")
            platform.console(owner, "$gray            $resultDetail")
            platform.console(owner, "")
            platform.console(owner, "$accent          ✦ §lPNFOLDER SUPPORT $dark• ${white}НА СВЯЗИ")
            platform.console(owner, "$white            Есть вопрос, ошибка, идея или просто нужна помощь?")
            platform.console(owner, "$gray            Пишите по любому поводу — разберёмся, подскажем и поможем.")
            platform.console(owner, "$gray            Нужен новый плагин? Обсудим бесплатную или заказную разработку.")
            platform.console(owner, "$accent            Discord $dark› ${white}§n${PnLibraryBrand.SUPPORT_URL}")
            platform.console(owner, "$dark          ───────────────────────────────────────────────")
            platform.console(owner, "")
        }

        private fun face() = when {
            shutdown -> "-.-"
            rows.any { it.status == "FAIL" } -> "x.x"
            rows.any { it.status == "WARN" } -> "o.o"
            else -> "^.^"
        }
    }

}
