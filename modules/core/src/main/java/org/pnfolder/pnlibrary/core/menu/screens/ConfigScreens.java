package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigApply;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigSetting;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuRoutes;
import org.pnfolder.pnlibrary.core.menu.MenuScreens;
import org.pnfolder.pnlibrary.core.menu.MenuSession;
import org.pnfolder.pnlibrary.core.menu.MenuViewer;
import org.pnfolder.pnlibrary.core.menu.style.ConfigLook;
import org.pnfolder.pnlibrary.core.menu.style.Fact;
import org.pnfolder.pnlibrary.core.menu.style.Icons;
import org.pnfolder.pnlibrary.core.menu.style.MenuConstants;
import org.pnfolder.pnlibrary.core.menu.style.MenuItems;
import org.pnfolder.pnlibrary.core.menu.style.Row;
import org.pnfolder.pnlibrary.core.menu.style.Style;
import org.pnfolder.pnlibrary.core.text.CoreFormatting;

/**
 * Menu screens for configuration files: plugins, their modules, their files, and the settings of one file that a
 * plugin marked as editable. How each button looks is {@link ConfigLook}.
 */
public final class ConfigScreens extends MenuScreens {
    /** What the first level lists: the plugins, or every file of every plugin with its plugin and module named. */
    enum Top {
        PLUGINS("Выбрать плагин"), ALL("Все конфигурации");

        final String label;

        Top(String label) {
            this.label = label;
        }
    }

    /** How the files of one plugin are laid out: grouped by module, or all of them in one list. */
    enum Layout {
        MODULES("По модулям"), FILES("Все файлы плагина");

        final String label;

        Layout(String label) {
            this.label = label;
        }
    }

    /** A file with its address split into plugin, module and path. */
    private static final class Located {
        final ConfigRow file;
        final String plugin;
        final String module;
        final String path;

        Located(ConfigRow file, String plugin, String module, String path) {
            this.file = file;
            this.plugin = plugin;
            this.module = module;
            this.path = path;
        }
    }

    private final ConfigEdits edits;

    public ConfigScreens(@NotNull MenuSession session, @NotNull MenuRoutes routes) {
        super(session, routes);
        this.edits = new ConfigEdits(session, routes);
    }

    private List<Located> located() {
        List<Located> all = new ArrayList<>();
        for (ConfigRow row : data().configs()) {
            ConfigId id = ConfigId.parse(row.getId());
            all.add(new Located(row, id.getPlugin().getValue(), id.getModule().getValue(), id.getPath()));
        }
        return all;
    }

    private static ConfigApply waiting(List<Located> files) {
        ConfigApply top = null;
        for (Located file : files) {
            ConfigApply pending = file.file.getPending();
            if (pending != null && (top == null || pending.compareTo(top) > 0)) {
                top = pending;
            }
        }
        return top;
    }

    private static int editable(List<Located> files) {
        int total = 0;
        for (Located file : files) {
            total += file.file.getEditable();
        }
        return total;
    }

    private static Set<String> modulesOf(List<Located> files) {
        Set<String> modules = new LinkedHashSet<>();
        for (Located file : files) {
            modules.add(file.module);
        }
        return modules;
    }

    /** The first level of the section. */
    public void configs(MenuViewer player, int page) {
        configsBy(player, Top.PLUGINS, page);
    }

    private void configsBy(MenuViewer player, Top top, int page) {
        List<Located> all = located();
        List<Row> rows = new ArrayList<>();
        if (top == Top.PLUGINS) {
            Map<String, List<Located>> byPlugin = new TreeMap<>();
            for (Located file : all) {
                List<Located> own = byPlugin.get(file.plugin);
                if (own == null) {
                    own = new ArrayList<>();
                    byPlugin.put(file.plugin, own);
                }
                own.add(file);
            }
            for (Map.Entry<String, List<Located>> entry : byPlugin.entrySet()) {
                List<Located> files = entry.getValue();
                rows.add(new Row(ConfigLook.plugin(entry.getKey(), modulesOf(files).size(), files.size(), editable(files), waiting(files)),
                    go(who -> pluginFiles(who, entry.getKey(), null, 0, top))));
            }
        } else {
            sort(all, true);
            for (Located file : all) {
                rows.add(fileRow(file, true, true, who -> configsBy(who, top, page)));
            }
        }
        Top other = top == Top.PLUGINS ? Top.ALL : Top.PLUGINS;
        list(player, "Конфигурации", rows, page, MenuConstants.PAGE_SIZE, (who, to) -> configsBy(who, top, to), routes::home,
            "Конфигураций нет", builder -> {
                builder.button(MenuConstants.FILTER, switcher("Показать", "Выбрать плагин или сразу увидеть все файлы.",
                    labels(Top.values()), top.ordinal()), go(who -> configsBy(who, other, 0)));
                builder.slot(MenuConstants.DATE_FILTER, ConfigLook.place("Плагинов", String.valueOf(pluginCount(all)),
                    "Файлов", String.valueOf(all.size())));
            });
    }

    private static int pluginCount(List<Located> all) {
        Set<String> plugins = new LinkedHashSet<>();
        for (Located file : all) {
            plugins.add(file.plugin);
        }
        return plugins.size();
    }

    private static List<String> labels(Enum<?>[] values) {
        List<String> labels = new ArrayList<>();
        for (Enum<?> value : values) {
            labels.add(value instanceof Top ? ((Top) value).label : ((Layout) value).label);
        }
        return labels;
    }

    private static void sort(List<Located> files, final boolean byPlugin) {
        Collections.sort(files, new Comparator<Located>() {
            @Override
            public int compare(Located a, Located b) {
                int plugin = byPlugin ? a.plugin.compareTo(b.plugin) : 0;
                if (plugin != 0) {
                    return plugin;
                }
                int module = a.module.compareTo(b.module);
                return module != 0 ? module : a.path.compareTo(b.path);
            }
        });
    }

    private static ItemSpec switcher(String label, String description, List<String> options, int selected) {
        return MenuItems.icon(Icons.SETTINGS, "HOPPER", label + ": " + options.get(selected), Style.lore(
            CoreFormatting.wrapText(description, CoreFormatting.WRAP_WIDTH, 2), Collections.<Fact>emptyList(),
            Style.choices(options, selected), Style.left("переключить")));
    }

    /** The second level: one plugin, by modules or all its files; a plugin with one module opens on its files. */
    private void pluginFiles(MenuViewer player, String plugin, @Nullable Layout layout, int page, Top top) {
        List<Located> files = new ArrayList<>();
        for (Located file : located()) {
            if (file.plugin.equals(plugin)) {
                files.add(file);
            }
        }
        List<String> modules = new ArrayList<>(modulesOf(files));
        Collections.sort(modules);
        Layout shown = layout != null ? layout : modules.size() > 1 ? Layout.MODULES : Layout.FILES;
        List<Row> rows = new ArrayList<>();
        if (shown == Layout.MODULES) {
            for (String module : modules) {
                List<Located> own = new ArrayList<>();
                for (Located file : files) {
                    if (file.module.equals(module)) {
                        own.add(file);
                    }
                }
                rows.add(new Row(ConfigLook.module(plugin, module, own.size(), editable(own), waiting(own)),
                    go(who -> moduleFiles(who, plugin, module, 0, top))));
            }
        } else {
            sort(files, false);
            for (Located file : files) {
                rows.add(fileRow(file, false, modules.size() > 1, who -> pluginFiles(who, plugin, shown, page, top)));
            }
        }
        Layout switchTo = shown == Layout.MODULES ? Layout.FILES : Layout.MODULES;
        list(player, plugin, rows, page, MenuConstants.PAGE_SIZE, (who, to) -> pluginFiles(who, plugin, shown, to, top),
            who -> configsBy(who, top, 0), "У плагина нет конфигураций", builder -> {
                builder.button(MenuConstants.FILTER, switcher("Показать", "Модули плагина или все его файлы подряд.",
                    labels(Layout.values()), shown.ordinal()), go(who -> pluginFiles(who, plugin, switchTo, 0, top)));
                builder.slot(MenuConstants.DATE_FILTER, ConfigLook.place("Плагин", plugin, "Модулей", String.valueOf(modules.size()),
                    "Файлов", String.valueOf(files.size())));
            });
    }

    /** The third level: the files of one module. */
    private void moduleFiles(MenuViewer player, String plugin, String module, int page, Top top) {
        List<Located> files = new ArrayList<>();
        for (Located file : located()) {
            if (file.plugin.equals(plugin) && file.module.equals(module)) {
                files.add(file);
            }
        }
        sort(files, false);
        List<Row> rows = new ArrayList<>();
        for (Located file : files) {
            rows.add(fileRow(file, false, false, who -> moduleFiles(who, plugin, module, page, top)));
        }
        String title = plugin + " · " + module;
        list(player, title.length() > MenuConstants.TITLE_LIMIT ? title.substring(0, MenuConstants.TITLE_LIMIT) : title, rows, page,
            MenuConstants.PAGE_SIZE, (who, to) -> moduleFiles(who, plugin, module, to, top),
            who -> pluginFiles(who, plugin, Layout.MODULES, 0, top), "В модуле нет конфигураций",
            builder -> builder.slot(MenuConstants.DATE_FILTER, ConfigLook.place("Плагин", plugin, "Модуль", module,
                "Файлов", String.valueOf(files.size()))));
    }

    private Row fileRow(Located entry, boolean withPlugin, boolean withModule, Consumer<MenuViewer> back) {
        ConfigRow file = entry.file;
        ItemSpec item = ConfigLook.file(file, entry.plugin, entry.module, entry.path, withPlugin, withModule);
        return new Row(item, file.getEditable() > 0 ? go(who -> card(who, file.getId(), 0, back)) : null);
    }

    /** The settings of one file; {@code back} returns to the screen it was opened from. */
    private void card(MenuViewer player, String id, int page, Consumer<MenuViewer> back) {
        ConfigRow file = null;
        for (ConfigRow row : data().configs()) {
            if (row.getId().equals(id)) {
                file = row;
            }
        }
        ConfigId address = ConfigId.parse(id);
        boolean readOnly = file != null && file.isReadOnly();
        List<Row> rows = new ArrayList<>();
        for (ConfigSetting setting : data().configSettings(id)) {
            rows.add(new Row(ConfigLook.setting(setting, readOnly), readOnly ? null : goClick((who, how) ->
                edits.change(who, id, setting, how, v -> card(v, id, page, back)))));
        }
        String path = address.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        String title = address.getModule().getValue() + " · " + name;
        String model = file == null ? "" : file.getModel();
        list(player, title.length() > MenuConstants.TITLE_LIMIT ? title.substring(0, MenuConstants.TITLE_LIMIT) : title, rows, page,
            MenuConstants.PAGE_SIZE, (who, to) -> card(who, id, to, back), back, "В этом файле нет настроек для игры",
            builder -> builder.slot(MenuConstants.DATE_FILTER, ConfigLook.place("Плагин", address.getPlugin().getValue(),
                "Модуль", address.getModule().getValue(), "Файл", path, "Класс", model)));
    }
}
