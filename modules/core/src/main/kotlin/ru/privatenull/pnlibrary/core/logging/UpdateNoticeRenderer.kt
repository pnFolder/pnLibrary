package ru.privatenull.pnlibrary.core.logging

/** Complete information needed to render one update notice. */
internal data class UpdateNotice(
    val product: String,
    val currentVersion: String,
    val latestVersion: String,
    val channel: String,
    val minimumJava: Int,
    val currentJava: Int,
    val downloadUrl: String,
    val downloaded: Boolean,
)

/** Produces the shared pnFolder console design for update notifications. */
internal object UpdateNoticeRenderer {
    fun render(notice: UpdateNotice): List<String> {
        val yellow = "§e"
        val green = "§a"
        val white = "§f"
        val gray = "§7"
        val dark = "§8"
        val channelName = notice.channel.uppercase()
        val channelDescription = channelDescription(notice.channel)
        val availability = if (notice.downloaded) "Загружена" else "Доступна"
        val summary = if (notice.downloaded) {
            "Новая версия загружена и проверена"
        } else {
            "Доступна новая версия"
        }

        return buildList {
            add("")
            add("$yellow          ━━━━━━━━━━━ §lНОВОЕ ОБНОВЛЕНИЕ$yellow ━━━━━━━━━━━")
            add("$yellow /\\_/\\")
            add("$yellow( ^o^ )     $white§l${notice.product} $dark› ${yellow}pnFolder")
            add("$yellow > ^ <      $gray$summary")
            add("")
            add("$dark            ┌ ${gray}Установлена: $white${notice.currentVersion}")
            add("$dark            ├ $gray$availability: $yellow§l${notice.latestVersion}")
            add("$dark            ├ ${gray}Канал: $white[ $channelName ] $dark• $gray$channelDescription")
            add("$dark            ├ ${gray}Java: $white${notice.currentJava} $dark• ${gray}требуется $white${notice.minimumJava}+")
            add("$dark            └ ${gray}Статус: ${status(notice.downloaded, yellow, green)}")
            add("")
            if (notice.downloaded) {
                add("$green          ✓ $white§lОбновление подготовлено")
                add("$gray            Оно установится автоматически после полного перезапуска сервера.")
            } else {
                add("$yellow          ◆ $white§lАвтоматическая загрузка отключена")
                add("$gray            Уведомления и ручное обновление продолжают работать.")
            }
            add("$dark            ${notice.downloadUrl}")
            add("")
            add("$dark          ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            add("")
        }
    }

    private fun channelDescription(channel: String): String = when (channel.lowercase()) {
        "stable" -> "стабильный канал"
        "rc" -> "канал RC"
        "beta" -> "бета-канал"
        "alpha" -> "альфа-канал"
        else -> "выбранный канал"
    }

    private fun status(downloaded: Boolean, yellow: String, green: String): String =
        if (downloaded) "$green§l✓ SHA-256 и JAR подтверждены"
        else "$yellow§lожидает ручной загрузки"
}
