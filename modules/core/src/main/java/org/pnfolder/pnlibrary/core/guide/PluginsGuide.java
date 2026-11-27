package org.pnfolder.pnlibrary.core.guide;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** The commands and permissions of one part of the library, for the command guide. */
final class PluginsGuide {
    private PluginsGuide() {
    }

    /** The section of the guide. */
    static @NotNull GuideSection section() {
        return new GuideSection(
            "Плагины, задачи и события",
            Arrays.asList(
                GuideEntry.of(
                    "/pn tasks [today|week|older|any] [all|active|failed|done] [страница]",
                    "pnlibrary.tasks",
                    "Задачи всех плагинов; по умолчанию сегодняшние."),
                GuideEntry.of(
                    "/pn tasks view <номер>",
                    "pnlibrary.tasks",
                    "Одна задача: для чего она, владелец, времена, последняя ошибка."),
                GuideEntry.of(
                    "/pn tasks run <номер>",
                    "pnlibrary.tasks",
                    "Запустить ждущую задачу сразу, если плагин не запрещает."),
                GuideEntry.of(
                    "/pn tasks cancel <номер>",
                    "pnlibrary.tasks",
                    "Остановить незавершённую задачу, если плагин не запрещает."),
                GuideEntry.of(
                    "/pn bus [all|listened|called|denied] [страница]",
                    "pnlibrary.events.bus",
                    "События плагинов со счётчиками слушателей и вызовов."),
                GuideEntry.of(
                    "/pn bus view <событие>",
                    "pnlibrary.events.bus",
                    "Одно событие: счётчики и слушатели по порядку вызова."),
                GuideEntry.of(
                    "/pn bus <listeners|slow|errors> [страница]",
                    "pnlibrary.events.bus",
                    "Все слушатели, самые медленные и те, что падали."),
                GuideEntry.of(
                    "/pn bus plugin <плагин> [страница]",
                    "pnlibrary.events.bus",
                    "Слушатели одного плагина со временем и сбоями."),
                GuideEntry.of(
                    "/pn bus top [calls|denied]",
                    "pnlibrary.events.bus",
                    "Десять самых вызываемых или самых отменяемых событий."),
                GuideEntry.of(
                    "/pn bus range [1h|24h|7d|30d]",
                    "pnlibrary.events.bus",
                    "Вызовы за час, сутки, неделю или месяц."),
                GuideEntry.of(
                    "/pn bus history [day <дата>|of <событие>] [страница]",
                    "pnlibrary.events.bus",
                    "История по дням, по одному дню или по одному событию."),
                GuideEntry.of(
                    "/pn bus export [csv|json]",
                    "pnlibrary.events.bus",
                    "Записать счётчики в файл рядом с историей."),
                GuideEntry.of(
                    "/pn bus trace [off] <событие>",
                    "pnlibrary.events.bus",
                    "Включить или выключить запись вызовов события."),
                GuideEntry.of(
                    "/pn bus traces [событие]",
                    "pnlibrary.events.bus",
                    "Записанные вызовы: что было до и после каждого слушателя."),
                GuideEntry.of(
                    "/pn bus clear",
                    "pnlibrary.events.bus",
                    "Забыть записанные вызовы."),
                GuideEntry.of(
                    "/pn placeholders [страница]",
                    "pnlibrary.placeholders",
                    "Список плейсхолдеров: тип, формат, имена в PlaceholderAPI."),
                GuideEntry.of(
                    "/pn placeholders info <адрес>",
                    "pnlibrary.placeholders",
                    "Как писать плейсхолдер: владелец, видимость, все формы адреса."),
                GuideEntry.of(
                    "/pn placeholders test <адрес> [uuid игрока]",
                    "pnlibrary.placeholders",
                    "Вычислить плейсхолдер вручную."),
                GuideEntry.of(
                    "/pnproxy channels, /pn channels",
                    "pnlibrary.channels",
                    "Каналы сообщений, которые плагины открыли на прокси или на игровом сервере."),
                GuideEntry.of(
                    "/pn actions [страница]",
                    "pnlibrary.actions",
                    "Именованные действия, требования и функции плагинов: владелец и видимость."),
                GuideEntry.of(
                    "/pn actions info <имя>",
                    "pnlibrary.actions",
                    "Как писать действие, требование или функцию: все формы с владельцем."),
                GuideEntry.of(
                    "/pn deps",
                    "pnlibrary.deps",
                    "Недостающие плагины: ссылки и код для установки."),
                GuideEntry.of(
                    "/pn deps install <код>",
                    "pnlibrary.deps",
                    "Скачать недостающие плагины по коду; потом перезапустите сервер.")));
    }
}
