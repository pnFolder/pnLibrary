package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTheme

/** Renders automatic update notifications with the same card used by console status. */
internal object UpdateAnnouncementRenderer {
    fun render(snapshot: UpdatePlanSnapshot): List<String> {
        val (subtitle, result) = when (snapshot.state) {
            UpdateState.UPDATE_AVAILABLE -> "найдены совместимые обновления" to "Доступно обновление"
            UpdateState.UPDATE_STAGED -> "обновление подготовлено" to "Перезапустите сервер"
            UpdateState.BLOCKED -> "обновление остановлено проверкой совместимости" to "Обновление не установлено"
            UpdateState.FAILED -> "проверка завершилась ошибкой" to "Проверка не выполнена"
            UpdateState.ROLLED_BACK -> "предыдущие версии подготовлены" to "Нужен перезапуск сервера"
            else -> return emptyList()
        }
        val theme = ConsoleTheme("§6", "§e", "§f", "§8", "§r")
        val card = ConsoleCard.builder(theme, "ПРОВЕРКА ОБНОВЛЕНИЙ")
            .mascot("^.^", "pnLibrary", subtitle)
            .blank()
        val changes = snapshot.plan?.changes.orEmpty()
        if (changes.isNotEmpty()) {
            card.section("ОБНОВЛЕНИЯ")
            changes.forEachIndexed { index, change ->
                val release = snapshot.plan?.selected?.firstOrNull { it.product == change.product }
                val channel = release?.channel?.let(::channelName) ?: "не указан"
                val api = release?.supportedApi?.let { "${it.minimum}–${it.maximum}" } ?: "не указан"
                val artifact = release?.artifacts?.firstOrNull()
                val platform = artifact?.platform?.displayName ?: "не указана"
                val java = artifact?.let { "${it.minimumJava}+" } ?: "не указана"
                val file = artifact?.file ?: "файл не указан"
                card.detail("Плагин", change.product.value)
                    .detail("Версия", "${change.from ?: "не установлена"} → ${change.to}")
                    .detail("Канал", channel)
                    .detail("Источник", if (release?.repository.isNullOrBlank()) "не указан" else "GitHub Releases")
                    .detail("Платформа", platform)
                    .detail("Совместимость API", api)
                    .detail("Java", java)
                    .detail("Почему выбрана", "версия новее и совместима")
                    .lastDetail("Файл", file)
                if (index != changes.lastIndex) card.blank()
            }
        } else if (snapshot.blockers.isNotEmpty()) {
            card.section("ПРИЧИНА")
            snapshot.blockers.forEachIndexed { index, blocker ->
                if (index == snapshot.blockers.lastIndex) card.lastItem(blocker.toString()) else card.item(blocker.toString())
            }
        } else if (!snapshot.message.isNullOrBlank()) {
            card.section("ПРИЧИНА").lastItem(snapshot.message!!)
        }
        card.blank().section("СОСТОЯНИЕ").lastItem(result).blank()
        if (snapshot.state == UpdateState.UPDATE_AVAILABLE) {
            card.section("ДЕЙСТВИЕ")
                .lastItem("Автозагрузка выключена; используйте /pn update <плагин>")
                .blank()
        }
        return card.status(result).build().render()
    }

    private fun channelName(channel: ru.privatenull.pnlibrary.api.updates.UpdateChannel): String = when (channel) {
        ru.privatenull.pnlibrary.api.updates.UpdateChannel.STABLE -> "стабильный канал"
        ru.privatenull.pnlibrary.api.updates.UpdateChannel.BETA -> "тестовый канал Beta"
        ru.privatenull.pnlibrary.api.updates.UpdateChannel.ALPHA -> "экспериментальный канал Alpha"
        ru.privatenull.pnlibrary.api.updates.UpdateChannel.DEV -> "разрабатываемый канал Dev"
    }
}
