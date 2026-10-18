package ru.privatenull.pnlibrary.core.updates.catalog

import ru.privatenull.pnlibrary.api.updates.ReleaseSummary
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.core.ui.ConsoleStyle

/** Renders automatic update notifications with the same card used by console status. */
internal object UpdateAnnouncementRenderer {
    fun render(snapshot: UpdatePlanSnapshot, registrations: List<UpdateSnapshot> = emptyList()): List<String> {
        val (subtitle, result) = when (snapshot.state) {
            UpdateState.UPDATE_AVAILABLE -> "найдены совместимые обновления" to "Доступно обновление"
            UpdateState.UPDATE_STAGED -> "обновление подготовлено" to "Перезапустите сервер"
            UpdateState.BLOCKED -> "обновление остановлено проверкой совместимости" to "Обновление не установлено"
            UpdateState.FAILED -> "проверка завершилась ошибкой" to "Проверка не выполнена"
            UpdateState.ROLLED_BACK -> "предыдущие версии подготовлены" to "Нужен перезапуск сервера"
            else -> return emptyList()
        }
        val card = ConsoleCard.builder(ConsoleStyle.THEME, "ПРОВЕРКА ОБНОВЛЕНИЙ")
            .mascot("^.^", "pnLibrary", subtitle)
            .blank()
        val changes = snapshot.plan?.changes.orEmpty()
        if (changes.isNotEmpty()) {
            card.section("ОБНОВЛЕНИЯ")
            changes.forEachIndexed { index, change ->
                val release = snapshot.plan?.selected?.firstOrNull { it.product == change.product }
                val channel = release?.channel?.let(UpdateChannelLabels::channelTitle) ?: "не указан"
                val api = release?.supportedApi?.let { "${it.minimum}–${it.maximum}" } ?: "не указан"
                val artifact = release?.artifacts?.firstOrNull()
                val platform = artifact?.platform?.displayName ?: "не указана"
                val java = artifact?.let { "${it.minimumJava}+" } ?: "не указана"
                val file = artifact?.file ?: "файл не указан"
                card.firstDetail("Плагин", change.product.value)
                    .detail("Версия", "${change.from ?: "не установлена"} → ${change.to}")
                    .detail("Канал", channel)
                    .detail("Описание", release?.description?.takeIf { it.isNotBlank() } ?: "не указано")
                    .detail("Источник", if (release?.repository.isNullOrBlank()) "не указан" else "GitHub Releases")
                    .detail("Платформа", platform)
                    .detail("Совместимость API", api)
                    .detail("Java", java)
                    .detail("Почему выбрана", "версия новее и совместима")
                    .lastDetail("Файл", file)
                release?.changelogUrl?.let { url ->
                    card.detail("Изменения", url)
                }
                if (index != changes.lastIndex) card.blank()
            }
            val histories = registrations.filter { it.availableReleases.isNotEmpty() }
            if (histories.isNotEmpty()) {
                card.blank().section("ПОСЛЕДНИЕ ВЕРСИИ")
                histories.forEachIndexed { index, registration ->
                    val latestByChannel = registration.availableReleases
                        .groupBy(ReleaseSummary::channel)
                        .mapValues { (_, releases) -> releases.maxByOrNull { it.version } }
                    latestByChannel.values.filterNotNull().forEach { release ->
                        card.firstDetail(UpdateChannelLabels.channelTitle(release.channel), release.version)
                            .lastDetail("Опубликована", release.publishedAt?.toString() ?: "дата неизвестна")
                    }
                    if (index != histories.lastIndex) card.blank()
                }
            }
        } else if (snapshot.blockers.isNotEmpty()) {
            card.section("ПРИЧИНА")
            snapshot.blockers.forEachIndexed { index, blocker ->
                if (index == snapshot.blockers.lastIndex) card.lastItem(blocker.toString()) else card.item(blocker.toString())
            }
        } else {
            snapshot.message?.takeIf(String::isNotBlank)?.let { card.section("ПРИЧИНА").lastItem(it) }
        }
        card.blank().section("СОСТОЯНИЕ").lastItem(result).blank()
        if (snapshot.state == UpdateState.UPDATE_AVAILABLE) {
            card.section("ДЕЙСТВИЕ")
                .lastItem("Автозагрузка выключена; используйте /pn update <плагин>")
                .blank()
        }
        return card.status(result).build().render()
    }
}
