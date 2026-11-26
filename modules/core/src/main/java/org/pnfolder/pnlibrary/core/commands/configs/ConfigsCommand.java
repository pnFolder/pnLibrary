package org.pnfolder.pnlibrary.core.commands.configs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatPalette;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigTypeInfo;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.core.commands.engine.ChatPager;

/**
 * {@code /pn configs}: every configuration file of every plugin under its identifier {@code plugin/module:path}.
 *
 * <pre>
 * /pn configs [page]                          lists the files with their class and state
 * /pn configs info &lt;id&gt;                      one file: where it lies, whether it is loaded, what was skipped, problems
 * /pn configs reload &lt;id&gt;                    reads the file again; the old value stays when the new one is broken
 * /pn configs validate &lt;id&gt;                  checks the loaded value without writing anything
 * /pn configs types &lt;name&gt;                   the words a polymorphic type understands and which plugin added each
 * /pn configs settings &lt;id&gt;                  the settings of a file that may be changed in game, with values
 * /pn configs edit &lt;id&gt; &lt;path&gt; &lt;value&gt;     changes one such setting and saves the file; needs pnlibrary.configs.edit
 * </pre>
 *
 * It never shows values from a file, so secrets cannot leak through it. Requires {@code pnlibrary.configs}; the console
 * always may.
 */
public final class ConfigsCommand {
    public static final String PERMISSION = "pnlibrary.configs";
    public static final String EDIT_PERMISSION = "pnlibrary.configs.edit";
    private static final String ROOT = "/pn configs";
    private static final int PAGE_SIZE = 10;

    private final PnLibrary library;
    private final ConfigSettingsPages settings;

    public ConfigsCommand(@NotNull PnLibrary library) {
        this.library = library;
        this.settings = new ConfigSettingsPages(library);
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("configs", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Конфигурации плагинов доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(context -> list(context, 1));
            root.argument("page", ArgumentType.integer(), page -> page.executes(context -> list(context, context.<Integer>get("page"))));
            root.literal("info", node -> withId(node, this::info));
            root.literal("reload", node -> withId(node, this::reload));
            root.literal("validate", node -> withId(node, this::validate));
            root.literal("settings", node -> withId(node, settings::show));
            root.literal("edit", this::editWords);
            root.literal("types", types -> types.argument("type", ArgumentType.string(), type -> {
                type.suggests(context -> typeNames());
                type.executes(context -> types(context, context.<String>get("type")));
            }));
        });
    }

    private void editWords(CommandNodeBuilder edit) {
        edit.permission(EDIT_PERMISSION);
        edit.noPermissionMessage("Менять настройки может тот, у кого есть право " + EDIT_PERMISSION + ".");
        edit.argument("id", ArgumentType.string(), id -> {
            id.suggests(context -> ids(true));
            id.argument("path", ArgumentType.string(), path -> path.argument("value", ArgumentType.greedyString(), value ->
                    value.executes(context -> {
                        ConfigId parsed = parseId(context);
                        if (parsed != null) {
                            settings.edit(context, parsed, context.<String>get("path"), context.<String>get("value"));
                        }
                    })));
        });
    }

    private List<String> ids(boolean writableOnly) {
        List<String> ids = new ArrayList<String>();
        for (ConfigInfo info : library.getConfigurations().all()) {
            if (!writableOnly || !info.getReadOnly()) {
                ids.add(info.getId().toString());
            }
        }
        return ids;
    }

    private List<String> typeNames() {
        List<String> names = new ArrayList<String>();
        for (Class<?> base : PolymorphicTypes.in(library.getConfigurations().all())) {
            names.add(base.getSimpleName());
        }
        return names;
    }

    private void withId(CommandNodeBuilder node, final BiConsumer<CommandContext, ConfigId> action) {
        node.argument("id", ArgumentType.string(), id -> {
            id.suggests(context -> ids(false));
            id.executes(context -> {
                ConfigId parsed = parseId(context);
                if (parsed != null) {
                    action.accept(context, parsed);
                }
            });
        });
    }

    /** The file named by the {@code id} argument, or {@code null} after telling the sender what is wrong with it. */
    private ConfigId parseId(CommandContext context) {
        String typed = context.<String>get("id");
        ConfigId id;
        try {
            id = ConfigId.parse(typed);
        } catch (RuntimeException invalid) {
            say(context, "Неверный адрес «" + typed + "»: " + invalid.getMessage(), ChatPalette.DANGER);
            return null;
        }
        if (library.getConfigurations().get(id) == null) {
            say(context, "Такого файла нет: " + id + ". Список: " + ROOT, ChatPalette.DANGER);
            return null;
        }
        return id;
    }

    private void say(CommandContext context, String text, TextColor color) {
        context.getSender().sendMessage(Component.text(text, color));
    }

    private void say(CommandContext context, String text) {
        say(context, text, ChatPalette.TEXT);
    }

    private ManagedConfig<?> config(ConfigId id) {
        ManagedConfig<?> config = library.getConfigurations().get(id);
        if (config == null) {
            throw new IllegalStateException("Configuration " + id + " is gone");
        }
        return config;
    }

    private void list(CommandContext context, int requested) {
        List<ConfigInfo> all = library.getConfigurations().all();
        int pages = ChatPager.pageCount(all.size(), PAGE_SIZE);
        int page = ChatPager.clamp(requested, pages);
        say(context, "Конфигурации: " + all.size() + ", страница " + page + "/" + pages, ChatPalette.ACCENT);
        for (ConfigInfo info : ChatPager.slice(all, page, PAGE_SIZE)) {
            String state;
            if (!info.getExists()) {
                state = "нет файла";
            } else if (!info.getSkippedUnknownTypes().isEmpty()) {
                state = "пропущено элементов: " + info.getSkippedUnknownTypes().size();
            } else {
                state = info.isLoaded() ? "загружен" : "не загружен";
            }
            String flags = info.getReadOnly() ? ", только чтение" : "";
            say(context, info.getId() + "  " + info.getModelType().getSimpleName() + "  (" + state + flags + ")",
                    info.isLoaded() ? ChatPalette.TEXT : ChatPalette.FAINT);
        }
        say(context, "Подробнее: " + ROOT + " info <адрес>", ChatPalette.FAINT);
    }

    private void info(CommandContext context, ConfigId id) {
        ConfigInfo info = config(id).getInfo();
        say(context, id.toString(), ChatPalette.ACCENT);
        say(context, "  плагин: " + info.getPlugin() + ", модуль: " + info.getModule() + ", корень: "
                + info.getRoot().name().toLowerCase(java.util.Locale.ROOT));
        say(context, "  файл: " + info.getFile() + (info.getExists() ? "" : " (не существует)"));
        say(context, "  класс: " + info.getModelType().getName());
        say(context, "  загружен: " + (info.isLoaded() ? "да, " + info.getLoadedAt() : "нет") + (info.getReadOnly() ? "; только чтение" : ""));
        if (!info.getSkippedUnknownTypes().isEmpty()) {
            say(context, "  пропущено из-за неизвестного type (остаётся в файле):", ChatPalette.WARN);
            for (String skipped : info.getSkippedUnknownTypes()) {
                say(context, "    " + skipped, ChatPalette.WARN);
            }
        }
        if (!info.getProblems().isEmpty()) {
            say(context, "  проблемы:", ChatPalette.DANGER);
            for (ConfigProblem problem : info.getProblems()) {
                say(context, "    " + problem.getPath() + ": " + problem.getMessage(), ChatPalette.DANGER);
            }
        }
    }

    private void reload(CommandContext context, ConfigId id) {
        ManagedConfig<?> config = config(id);
        try {
            config.reload();
            say(context, id + " прочитан заново.", ChatPalette.OK);
            ConfigInfo info = config.getInfo();
            if (info != null && !info.getSkippedUnknownTypes().isEmpty()) {
                say(context, "Пропущено элементов с неизвестным type: " + info.getSkippedUnknownTypes().size()
                        + ". Подробнее: " + ROOT + " info " + id, ChatPalette.WARN);
            }
        } catch (Exception broken) {
            say(context, id + " не прочитан, прежнее значение сохранено: " + broken.getMessage(), ChatPalette.DANGER);
        }
    }

    private void validate(CommandContext context, ConfigId id) {
        ManagedConfig<?> config = config(id);
        if (!config.isLoaded()) {
            say(context, id + " не загружен, проверять нечего.", ChatPalette.WARN);
            return;
        }
        List<ConfigProblem> problems = config.validate();
        if (problems.isEmpty()) {
            say(context, id + ": проблем нет.", ChatPalette.OK);
            return;
        }
        say(context, id + ": проблем " + problems.size(), ChatPalette.DANGER);
        for (ConfigProblem problem : problems) {
            say(context, "  " + problem.getPath() + ": " + problem.getMessage(), ChatPalette.DANGER);
        }
    }

    private void types(CommandContext context, String typed) {
        Class<?> base = null;
        for (Class<?> candidate : PolymorphicTypes.in(library.getConfigurations().all())) {
            if (candidate.getSimpleName().equalsIgnoreCase(typed) || candidate.getName().equals(typed)) {
                base = candidate;
                break;
            }
        }
        if (base == null) {
            say(context, "Такого типа нет среди зарегистрированных конфигураций: " + typed, ChatPalette.DANGER);
            return;
        }
        List<ConfigTypeInfo> words = library.getConfigurations().types(base);
        say(context, base.getSimpleName() + ": слов " + words.size(), ChatPalette.ACCENT);
        for (ConfigTypeInfo word : words) {
            String who = word.getOwner() == null ? "объявлен в самом типе" : "добавил " + word.getOwner() + " в " + word.getTarget();
            String aliases = word.getAliases().isEmpty() ? "" : " (также " + String.join(", ", word.getAliases()) + ")";
            say(context, "  " + word.getName() + aliases + " → " + word.getImplementation().getSimpleName() + "; " + who
                    + "; приоритет " + word.getPriority());
        }
    }
}
