package org.pnfolder.pnlibrary.core.guide;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** The commands and permissions of one part of the library, for the command guide. */
final class UpdatesGuide {
    private UpdatesGuide() {
    }

    /** The section of the guide. */
    static @NotNull GuideSection section() {
        return new GuideSection(
            "Обновления",
            Arrays.asList(
                GuideEntry.of(
                    "/pn",
                    LibraryCommandGuide.ADMIN,
                    "Список плагинов и их обновлений. Без этого права команда покажет только то, что разрешено вам.")
                    .note("Сама команда /pn доступна всем."),
                GuideEntry.of(
                    "/pn status [плагин]",
                    "pnlibrary.updates.view",
                    "Состояние обновлений: версии, канал, что готово к установке.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn check",
                    "pnlibrary.updates.view",
                    "Проверить прямо сейчас, есть ли новые версии плагинов.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn updates [list|available] [страница]",
                    "pnlibrary.updates.view",
                    "Каталог плагинов; available показывает только те, что можно обновить.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn updates info <плагин>",
                    "pnlibrary.updates.view",
                    "Карточка плагина: версии, канал, состояние.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn updates settings <плагин>",
                    "pnlibrary.updates.view",
                    "Настройки обновления плагина: канал и пауза.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn updates history [страница]",
                    "pnlibrary.updates.view",
                    "История обновлений всех плагинов.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn updates history plugin <плагин> [страница]",
                    "pnlibrary.updates.view",
                    "История обновлений одного плагина.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn updates set <плагин> channel <dev|default>",
                    "pnlibrary.updates.settings",
                    "Выбрать канал выпусков; default возвращает выбор плагина.")
                    .note("Сейчас публикуется только канал dev."),
                GuideEntry.of(
                    "/pn updates set <плагин> hold <срок|off>",
                    "pnlibrary.updates.settings",
                    "Поставить обновления плагина на паузу от 1m до 5d (например 3d) или снять паузу."),
                GuideEntry.of(
                    "/pn updates backups [plugin <плагин>] [since <7d|дата>] [sort <new|old|plugin>] [страница]",
                    "pnlibrary.updates.rollback",
                    "Резервные наборы, сохранённые перед обновлениями; можно отфильтровать и отсортировать."),
                GuideEntry.of(
                    "/pn updates backups restore <набор> <плагин> [confirm] <база>",
                    "pnlibrary.updates.settings",
                    "Вернуть сохранённую базу данных плагина; без confirm только предупредит.")
                    .note("Нужно и право pnlibrary.updates.rollback."),
                GuideEntry.of(
                    "/pn updates backups files <набор> <плагин>",
                    "pnlibrary.updates.settings",
                    "Выложить сохранённые файлы плагина в папку, чтобы взять их руками.")
                    .note("Нужно и право pnlibrary.updates.rollback."),
                GuideEntry.of(
                    "/pn updates backups delete <набор>",
                    "pnlibrary.updates.settings",
                    "Удалить резервный набор.")
                    .note("Нужно и право pnlibrary.updates.rollback."),
                GuideEntry.of(
                    "/pn updates rollback <набор> [configs]",
                    "pnlibrary.updates.rollback",
                    "Откатиться к резервному набору (с configs вместе с конфигурациями); спросит подтверждение."),
                GuideEntry.of(
                    "/pn update <плагин>",
                    "pnlibrary.updates.download",
                    "Найти, скачать и проверить обновление плагина; оно применится после перезапуска.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn update confirm <код>",
                    "pnlibrary.updates.download",
                    "Подтвердить действие по коду из сообщения (откат, перезапуск).")
                    .orAdmin()
                    .note("Само действие требует своего права: rollback или restart."),
                GuideEntry.of(
                    "/pn update rollback",
                    "pnlibrary.updates.rollback",
                    "Откатить весь подготовленный план обновления; спросит подтверждение.")
                    .note("Нужно и право на /pn update (updates.download или admin)."),
                GuideEntry.of(
                    "/pn update restart",
                    "pnlibrary.updates.restart",
                    "Перезапустить сервер, чтобы применить подготовленные обновления: сразу, через 5 минут или " +
                        "когда все выйдут.")
                    .scope(GuideScope.GAME_SERVER)
                    .note("Нужно и право на /pn update. На прокси перезапускайте прокси как обычно."),
                GuideEntry.of(
                    "/pn update restart cancel",
                    "pnlibrary.updates.download",
                    "Отменить запланированный перезапуск.")
                    .orAdmin()
                    .scope(GuideScope.GAME_SERVER),
                GuideEntry.of(
                    "(уведомления об обновлениях)",
                    "pnlibrary.updates.notify",
                    "Получать сообщения об обновлениях при входе и по таймеру.")
                    .orAdmin(),
                GuideEntry.of(
                    "(установка новых компонентов)",
                    "pnlibrary.updates.install",
                    "Зарезервировано под установку новых компонентов.")
                    .note("Сейчас ни одна команда это право не проверяет.")));
    }
}
