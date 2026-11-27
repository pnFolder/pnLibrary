package org.pnfolder.pnlibrary.core.commands.settings;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.style.ChatPalette;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.commands.engine.ChatPager;
import org.pnfolder.pnlibrary.core.settings.SettingApply;
import org.pnfolder.pnlibrary.core.settings.SettingChange;
import org.pnfolder.pnlibrary.core.settings.SettingException;
import org.pnfolder.pnlibrary.core.settings.SettingKind;
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook;
import org.pnfolder.pnlibrary.core.settings.SettingSpec;
import org.pnfolder.pnlibrary.core.settings.SettingTexts;
import org.pnfolder.pnlibrary.core.settings.SettingValue;
import org.pnfolder.pnlibrary.core.settings.SettingsRegistry;
import org.pnfolder.pnlibrary.core.settings.SettingsStore;

/**
 * {@code /pn settings}: every setting of the library, changed in game; there is no settings file to edit.
 *
 * <pre>
 * /pn settings [list] [page]          all settings with their values
 * /pn settings get &lt;key&gt;             one setting: value, default, limits, when a change starts to work
 * /pn settings set &lt;key&gt; &lt;value&gt;     changes a setting and saves it in data/settings.json
 * /pn settings reset &lt;key&gt;           returns a setting to its default
 * /pn settings pick &lt;key&gt; [filter]   a player opens the menu list of a choice setting, narrowed to names with the filter
 * </pre>
 *
 * Requires {@code pnlibrary.settings}; the console always may. The list of settings is {@link SettingsRegistry}.
 */
public final class SettingsCommand {
    public static final String PERMISSION = "pnlibrary.settings";
    private static final String ROOT = "/pn settings";
    private static final int PAGE_SIZE = 6;

    private final SettingsStore settings;
    private final SettingPickerHook picker;

    public SettingsCommand(@NotNull SettingsStore settings, @NotNull SettingPickerHook picker) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.picker = Objects.requireNonNull(picker, "picker");
    }

    public SettingsCommand(@NotNull SettingsStore settings) {
        this(settings, new SettingPickerHook());
    }

    @NotNull
    public CommandDefinition definition() {
        return CommandDefinition.command("settings", root -> {
            root.permission(PERMISSION);
            root.noPermissionMessage("Настройки библиотеки доступны администраторам. Нужно право " + PERMISSION + ".");
            root.consoleBypassesPermission();
            root.executes(context -> list(context, 1));
            root.argument("page", ArgumentType.integer(), page -> page.executes(context -> list(context, context.<Integer>get("page"))));
            root.literal("list", list -> {
                list.executes(context -> list(context, 1));
                list.argument("page", ArgumentType.integer(), page -> page.executes(context -> list(context, context.<Integer>get("page"))));
            });
            getWord(root);
            setWord(root);
            pickWord(root);
            resetWord(root);
        });
    }

    private void getWord(CommandBuilder root) {
        root.literal("get", get -> get.argument("key", ArgumentType.string(), key -> {
            key.suggests(context -> keys());
            key.executes(context -> show(context, context.<String>get("key")));
        }));
    }

    private void setWord(CommandBuilder root) {
        root.literal("set", set -> set.argument("key", ArgumentType.string(), key -> {
            key.suggests(context -> keys());
            key.argument("value", ArgumentType.greedyString(), value -> {
                value.suggests(context -> values(typedKey(context)));
                value.executes(context -> change(context, context.<String>get("key"),
                        name -> settings.set(name, context.<String>get("value"))));
            });
        }));
    }

    private void pickWord(CommandBuilder root) {
        root.literal("pick", pick -> pick.argument("key", ArgumentType.string(), key -> {
            key.suggests(context -> choiceKeys());
            key.executes(context -> pick(context, context.<String>get("key"), ""));
            key.argument("filter", ArgumentType.greedyString(), filter -> filter.executes(
                    context -> pick(context, context.<String>get("key"), context.<String>get("filter"))));
        }));
    }

    private void resetWord(CommandBuilder root) {
        root.literal("reset", reset -> reset.argument("key", ArgumentType.string(), key -> {
            key.suggests(context -> changedKeys());
            key.executes(context -> change(context, context.<String>get("key"), settings::reset));
        }));
    }

    private static String typedKey(CommandContext context) {
        Object parsed = context.getParsedValues().get("key");
        if (parsed instanceof String) {
            return (String) parsed;
        }
        return context.getArguments().size() > 1 ? context.getArguments().get(1) : null;
    }

    private List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            keys.add(spec.getKey());
        }
        return keys;
    }

    private List<String> choiceKeys() {
        List<String> keys = new ArrayList<>();
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            if (spec.getChoices() != null) {
                keys.add(spec.getKey());
            }
        }
        return keys;
    }

    private List<String> changedKeys() {
        List<String> keys = new ArrayList<>();
        for (SettingValue value : settings.values()) {
            if (value.getChanged()) {
                keys.add(value.getSpec().getKey());
            }
        }
        return keys;
    }

    /** Opens the menu list of the choice setting {@code key} for the player, narrowed to {@code filter}. */
    private void pick(CommandContext context, String key, String filter) {
        SettingSpec spec = SettingsRegistry.get(key);
        if (spec == null || spec.getChoices() == null) {
            say(context, "Нет настройки с выбором из списка: " + key + ". Есть: " + String.join(", ", choiceKeys()) + ".", ChatPalette.DANGER);
            return;
        }
        if (AudienceExtensions.isConsole(context.getSender())) {
            say(context, "Список открывается в меню игрока. В консоли: " + ROOT + " set " + spec.getKey() + " <значение>.", ChatPalette.WARN);
            return;
        }
        SettingPickerHook.Opener opener = picker.getOpener();
        boolean opened = opener != null && opener.open(context, spec.getKey(), filter.trim());
        if (!opened) {
            say(context, "Меню здесь недоступно. Откройте /pn gui на игровом сервере или используйте " + ROOT + " set "
                    + spec.getKey() + " <значение>.", ChatPalette.WARN);
        }
    }

    /** What is worth proposing for the value of {@code key}: the words of a switch, the examples, the default. */
    private List<String> values(String key) {
        SettingSpec spec = key == null ? null : SettingsRegistry.get(key);
        if (spec == null) {
            return new ArrayList<>();
        }
        List<String> proposals = new ArrayList<>();
        if (spec.getKind() == SettingKind.SWITCH) {
            proposals.add(SettingTexts.switchWord(true));
            proposals.add(SettingTexts.switchWord(false));
        } else if (spec.getKind() == SettingKind.NUMBER) {
            if (spec.getMin() != null) {
                proposals.add(spec.getMin().toString());
            }
            if (spec.getMax() != null) {
                proposals.add(spec.getMax().toString());
            }
        } else {
            proposals.addAll(spec.getExamples());
            if (spec.getKind() == SettingKind.CHOICE && spec.getChoices() != null) {
                proposals.addAll(spec.getChoices().getAll());
            }
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        distinct.add(spec.display(spec.getDefault()));
        distinct.addAll(proposals);
        return new ArrayList<>(distinct);
    }

    private void say(CommandContext context, String text, TextColor color) {
        context.getSender().sendMessage(Component.text(text, color));
    }

    private void list(CommandContext context, int requested) {
        List<SettingValue> all = settings.values();
        int pages = ChatPager.pageCount(all.size(), PAGE_SIZE);
        int page = ChatPager.clamp(requested, pages);
        say(context, "Настройки библиотеки: " + all.size() + ", страница " + page + "/" + pages, ChatPalette.ACCENT);
        for (SettingValue entry : ChatPager.slice(all, page, PAGE_SIZE)) {
            SettingSpec spec = entry.getSpec();
            String mark = entry.getChanged() ? "  (по умолчанию " + spec.display(spec.getDefault()) + ")" : "";
            say(context, spec.getKey() + " = " + spec.display(entry.getCurrent()) + mark,
                    entry.getChanged() ? ChatPalette.OK : ChatPalette.TEXT);
        }
        if (pages > 1) {
            context.getSender().sendMessage(ChatPager.pager(new ChatKit(ChatReader.of(context.getSender())), ROOT + " list", page, pages));
        }
        say(context, "Подробнее: " + ROOT + " get <ключ>. Изменить: " + ROOT + " set <ключ> <значение>.", ChatPalette.FAINT);
    }

    private void show(CommandContext context, String key) {
        SettingValue entry;
        try {
            entry = settings.value(key);
        } catch (SettingException unknown) {
            say(context, unknown.getMessage() == null ? "" : unknown.getMessage(), ChatPalette.DANGER);
            return;
        }
        SettingSpec spec = entry.getSpec();
        say(context, spec.getKey(), ChatPalette.ACCENT);
        say(context, "  значение: " + spec.display(entry.getCurrent()) + (entry.getChanged() ? "" : " (по умолчанию)"), ChatPalette.TEXT);
        if (entry.getChanged()) {
            say(context, "  по умолчанию: " + spec.display(spec.getDefault()), ChatPalette.TEXT);
        }
        say(context, "  тип: " + spec.getKind().getLabel(), ChatPalette.TEXT);
        if (!spec.range().isEmpty()) {
            say(context, "  допустимо: " + spec.range(), ChatPalette.TEXT);
        }
        if (!spec.getExamples().isEmpty()) {
            say(context, "  примеры: " + String.join(", ", spec.getExamples()), ChatPalette.TEXT);
        }
        say(context, "  заработает: " + spec.getApplies().getLabel(), spec.getApplies() == SettingApply.LIVE ? ChatPalette.TEXT : ChatPalette.WARN);
        say(context, "  " + spec.getDescription(), ChatPalette.FAINT);
    }

    private void change(CommandContext context, String key, Function<String, SettingChange> action) {
        try {
            SettingChange result = action.apply(key);
            SettingSpec spec = result.getSpec();
            say(context, spec.getKey() + ": " + spec.display(result.getBefore()) + " → " + spec.display(result.getAfter()) + ". Сохранено.",
                    ChatPalette.OK);
            if (spec.getApplies() == SettingApply.RESTART) {
                say(context, "Начнёт работать " + spec.getApplies().getLabel() + " сервера.", ChatPalette.WARN);
            }
        } catch (SettingException refused) {
            say(context, "Не изменено: " + refused.getMessage(), ChatPalette.DANGER);
        }
    }
}
