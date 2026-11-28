package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.chat.style.ChatTone;
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState;
import org.pnfolder.pnlibrary.core.commands.modules.ModuleLabels;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;
import org.pnfolder.pnlibrary.core.menu.session.MenuClickHandler;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.session.MenuSession;
import org.pnfolder.pnlibrary.core.menu.session.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.Menus;
import org.pnfolder.pnlibrary.core.menu.data.ModuleInfo;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.MenuPermissions;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.menu.style.Tone;

/** Menu screens for the modules of plugins: a list and a card with the same switches as {@code /pn modules}. */
public final class ModuleScreens extends MenuScreens {
    public ModuleScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
    }

    /** The modules of every plugin, or only those of {@code plugin} when it is given. */
    public void modules(MenuViewer player, @Nullable String plugin, int page, Consumer<MenuViewer> back) {
        List<Row> rows = new ArrayList<>();
        for (ModuleInfo info : data().modules()) {
            if (plugin != null && !info.getKey().toLowerCase(Locale.ROOT).startsWith(plugin.toLowerCase(Locale.ROOT) + ":")) {
                continue;
            }
            rows.add(new Row(item(info), go(who -> module(who, info.getKey(), v -> modules(v, plugin, page, back)))));
        }
        list(player, plugin == null ? "Модули плагинов" : "Модули: " + plugin, rows, page, MenuConstants.PAGE_SIZE,
            (who, to) -> modules(who, plugin, to, back), back, "Модулей нет");
    }

    private static ItemSpec item(ModuleInfo info) {
        String material;
        switch (info.getState()) {
            case ENABLED:
                material = "LIME_DYE";
                break;
            case DISABLED:
                material = "GRAY_DYE";
                break;
            default:
                material = "BARRIER";
                break;
        }
        List<Fact> parameters = new ArrayList<>();
        if (info.getDescription() != null) {
            parameters.add(new Fact("О модуле", take(info.getDescription(), 60)));
        }
        parameters.add(new Fact("Состояние", ModuleLabels.label(info.getState())));
        if (info.isControllable()) {
            parameters.add(new Fact("С сервером", info.getAutoStart() ? "включается" : "не включается"));
        } else {
            parameters.add(new Fact("Переключатель", "плагин не дал"));
        }
        int libraries = info.getLibraries().size() + info.getPluginLibraries().size();
        if (libraries > 0) {
            parameters.add(new Fact("Библиотек", String.valueOf(libraries)));
        }
        if (info.getError() != null) {
            parameters.add(new Fact("Ошибка", "✖ " + take(info.getError(), 60)));
        }
        return MenuItems.icon(null, material, info.getTitle(),
            Style.lore(Collections.singletonList(info.getKey()), parameters, Style.left("открыть")),
            Tone.of(info.getState() == ModuleState.FAILED, false, info.getState() == ModuleState.ENABLED,
                info.getState() == ModuleState.DISABLED));
    }

    private static String take(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private void module(MenuViewer player, String key, Consumer<MenuViewer> back) {
        ModuleInfo info = find(key);
        if (info == null) {
            tell(player, ChatTone.INFO, "Этот модуль уже исчез из списка.");
            back.accept(player);
            return;
        }
        MenuBuilder builder = MenuItems.menuFrame(
            Menus.chest(MenuItems.menuTitle(info.getTitle())).rows(MenuConstants.DETAIL_ROWS), MenuConstants.DETAIL_ROWS);
        builder.slot(4, item(info));
        int total = info.getLibraries().size() + info.getPluginLibraries().size();
        builder.button(22, MenuItems.section(Icons.CHANGELOG, "BOOKSHELF", "Библиотеки",
            "Что модуль и его плагин подключили для работы.", new Fact("Всего", String.valueOf(total))),
            go(who -> routes.libraries(who, key, 0, v -> module(v, key, back))));
        if (info.isControllable()) {
            controls(builder, info, v -> module(v, key, back));
        }
        builder.button(MenuConstants.DETAIL_BACK, MenuItems.backItem(), go(back));
        show(player, builder.build());
    }

    private ModuleInfo find(String key) {
        for (ModuleInfo info : data().modules()) {
            if (info.getKey().equals(key)) {
                return info;
            }
        }
        return null;
    }

    private void controls(MenuBuilder builder, ModuleInfo info, Consumer<MenuViewer> reopen) {
        String key = info.getKey();
        boolean on = info.getState() == ModuleState.ENABLED;
        builder.button(10, control(Icons.CHECK, "EMERALD", "Включить", "Запустить модуль.", !on), act(key, "enable", "включён", reopen));
        builder.button(11, control(Icons.PROBLEMS, "REDSTONE", "Выключить", "Остановить до следующего запуска сервера.", on),
            act(key, "disable", "выключен", reopen));
        builder.button(12, control(Icons.UPDATES, "REPEATER", "Перезапустить", "Выключить и включить заново.", true),
            act(key, "restart", "перезапущен", reopen));
        builder.button(13, control(Icons.SETTINGS, "WRITABLE_BOOK", "Перечитать",
            "Перечитать настройки; если сам не умеет, перезапустится.", on), act(key, "reload", "перечитан", reopen));
        String action = info.getAutoStart() ? "auto-off" : "auto-on";
        String label = info.getAutoStart() ? "Не включать с сервером" : "Включать с сервером";
        builder.button(15, control(Icons.HISTORY, "CLOCK", label, "Сохраняется между перезапусками сервера.", true),
            act(key, action, "настроен", reopen));
    }

    private static ItemSpec control(String texture, String material, String label, String description, boolean available) {
        List<String> lore = available
            ? Style.lore(Collections.singletonList(description), Collections.emptyList(), Style.left("выполнить"))
            : Style.lore(Collections.singletonList(description), Collections.emptyList());
        return MenuItems.icon(texture, available ? material : "COAL", label, lore, available ? Tone.NORMAL : Tone.MUTED);
    }

    private MenuClickHandler act(String key, String action, String done, Consumer<MenuViewer> reopen) {
        return go(who -> allowed(who, MenuPermissions.MODULES, () -> away(who, () -> {
            ModuleInfo changed = null;
            Exception failure = null;
            try {
                changed = data().moduleAction(key, action);
            } catch (Exception error) {
                failure = error;
            }
            final ModuleInfo result = changed;
            final Exception error = failure;
            sync(() -> {
                if (error != null) {
                    tell(who, ChatTone.FAILURE, "Не получилось", error.getMessage());
                } else if (result.getError() != null) {
                    tell(who, ChatTone.FAILURE, "Модуль " + result.getTitle() + ": ошибка", result.getError());
                } else {
                    tell(who, ChatTone.SUCCESS, "Модуль " + result.getTitle() + " " + done);
                }
                reopen.accept(who);
            });
        })));
    }
}
