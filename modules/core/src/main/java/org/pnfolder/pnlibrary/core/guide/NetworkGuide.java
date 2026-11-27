package org.pnfolder.pnlibrary.core.guide;

import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/** The commands and permissions of one part of the library, for the command guide. */
final class NetworkGuide {
    private NetworkGuide() {
    }

    /** The section of the guide. */
    static @NotNull GuideSection section() {
        return new GuideSection(
            "Сеть серверов",
            Arrays.asList(
                GuideEntry.of(
                    "/pn servers",
                    "pnlibrary.servers",
                    "Серверы сети: версия pnLibrary, игроки, плагины.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pn servers <сервер>",
                    "pnlibrary.servers",
                    "Один сервер: платформа и состояние обновления каждого плагина.")
                    .orAdmin(),
                GuideEntry.of(
                    "/pnproxy servers add <имя> <хост:порт>",
                    "pnlibrary.servers.manage",
                    "Добавить сервер на прокси, пока он работает (после перезапуска пропадёт).")
                    .orAdmin()
                    .note("Нужно и право на /pn servers."),
                GuideEntry.of(
                    "/pnproxy servers remove <имя>",
                    "pnlibrary.servers.manage",
                    "Убрать сервер, добавленный командой или плагином. Свои серверы прокси не убираются.")
                    .orAdmin()
                    .note("Нужно и право на /pn servers."),
                GuideEntry.of(
                    "/pn servers <сервер> check",
                    "pnlibrary.updates.view",
                    "Попросить сервер проверить обновления.")
                    .note("Нужно и право на /pn servers."),
                GuideEntry.of(
                    "/pn servers <сервер> update <плагин>",
                    "pnlibrary.updates.download",
                    "Попросить сервер скачать обновление плагина.")
                    .note("Нужно и право на /pn servers."),
                GuideEntry.of(
                    "/pn servers <сервер> auto <плагин> <on|off>",
                    "pnlibrary.updates.settings",
                    "Включить или выключить автозагрузку плагина на сервере.")
                    .note("Нужно и право на /pn servers."),
                GuideEntry.of(
                    "/pn groups",
                    "pnlibrary.groups",
                    "Группы серверов и сколько серверов в порядке.")
                    .orAdmin()
                    .scope(GuideScope.PROXY),
                GuideEntry.of(
                    "/pn groups <info|delete> <группа>",
                    "pnlibrary.groups",
                    "Состав группы или удаление группы.")
                    .orAdmin()
                    .scope(GuideScope.PROXY),
                GuideEntry.of(
                    "/pn groups create <группа>",
                    "pnlibrary.groups",
                    "Создать пустую группу.")
                    .orAdmin()
                    .scope(GuideScope.PROXY),
                GuideEntry.of(
                    "/pn groups <join|leave> <группа> <сервер>",
                    "pnlibrary.groups",
                    "Добавить сервер в группу или убрать.")
                    .orAdmin()
                    .scope(GuideScope.PROXY),
                GuideEntry.of(
                    "/pn groups <require|drop> <группа> <плагин>",
                    "pnlibrary.groups",
                    "Плагин обязателен для группы или больше нет.")
                    .orAdmin()
                    .scope(GuideScope.PROXY),
                GuideEntry.of(
                    "/pn groups <server|skip|extra|reset> <группа> <сервер> [плагин]",
                    "pnlibrary.groups",
                    "Чем один сервер отличается от группы: показать, не нужен плагин, нужен лишний, сбросить.")
                    .orAdmin()
                    .scope(GuideScope.PROXY),
                GuideEntry.of(
                    "/pn groups of <сервер>",
                    "pnlibrary.groups",
                    "В каких группах состоит сервер.")
                    .orAdmin()
                    .scope(GuideScope.PROXY),
                GuideEntry.of(
                    "/pn groups check <группа>",
                    "pnlibrary.updates.view",
                    "Все серверы группы проверят обновления.")
                    .scope(GuideScope.PROXY)
                    .note("Нужно и право на /pn groups."),
                GuideEntry.of(
                    "/pn groups update <группа>",
                    "pnlibrary.updates.download",
                    "Все серверы группы скачают доступные обновления.")
                    .scope(GuideScope.PROXY)
                    .note("Нужно и право на /pn groups.")));
    }
}
