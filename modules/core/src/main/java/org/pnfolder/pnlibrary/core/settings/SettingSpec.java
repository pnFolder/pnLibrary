package org.pnfolder.pnlibrary.core.settings;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One setting: its key, kind, limits, description and how to read and change it in {@link LibrarySettings}. */
public final class SettingSpec {
    private final String key;
    private final String title;
    private final String field;
    private final SettingKind kind;
    private final String description;
    private final SettingApply applies;
    private final Long min;
    private final Long max;
    private final List<String> examples;
    private final ChoiceSource choices;
    private final Function<LibrarySettings, Object> read;
    private final BiFunction<LibrarySettings, Object, LibrarySettings> write;
    private final Function<String, Object> parse;
    private final Function<Object, String> show;

    /**
     * Creates a setting.
     *
     * @param key the name used in commands and in {@code settings.json}, such as {@code downloads.enabled}
     * @param title the short name shown in the menu
     * @param field the property of {@link LibrarySettings} it controls
     * @param min lowest number, or lowest length in milliseconds for a time; null for none
     * @param max highest number, or highest length in milliseconds for a time, or longest text; null for none
     * @param choices the list a {@link SettingKind#CHOICE} setting is chosen from; null for none
     * @param read takes the value out of the settings
     * @param write puts a parsed value into a copy of the settings
     * @param parse turns the text an administrator typed into a value, or throws {@link SettingException}
     * @param show turns a value into the text an administrator would type
     */
    public SettingSpec(@NotNull String key, @NotNull String title, @NotNull String field, @NotNull SettingKind kind,
                       @NotNull String description, @NotNull SettingApply applies, @Nullable Long min,
                       @Nullable Long max, @NotNull List<String> examples, @Nullable ChoiceSource choices,
                       @NotNull Function<LibrarySettings, Object> read,
                       @NotNull BiFunction<LibrarySettings, Object, LibrarySettings> write,
                       @NotNull Function<String, Object> parse, @NotNull Function<Object, String> show) {
        this.key = Objects.requireNonNull(key, "key");
        this.title = Objects.requireNonNull(title, "title");
        this.field = Objects.requireNonNull(field, "field");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.description = Objects.requireNonNull(description, "description");
        this.applies = Objects.requireNonNull(applies, "applies");
        this.min = min;
        this.max = max;
        this.examples = Collections.unmodifiableList(new ArrayList<>(examples));
        this.choices = choices;
        this.read = Objects.requireNonNull(read, "read");
        this.write = write;
        this.parse = Objects.requireNonNull(parse, "parse");
        this.show = Objects.requireNonNull(show, "show");
    }

    /** The name used in commands and in {@code settings.json}. */
    public @NotNull String getKey() {
        return key;
    }

    /** The short name shown in the menu. */
    public @NotNull String getTitle() {
        return title;
    }

    /** The property of {@link LibrarySettings} it controls. */
    public @NotNull String getField() {
        return field;
    }

    /** What kind of value it holds. */
    public @NotNull SettingKind getKind() {
        return kind;
    }

    /** What the setting is for. */
    public @NotNull String getDescription() {
        return description;
    }

    /** When a change starts to work. */
    public @NotNull SettingApply getApplies() {
        return applies;
    }

    /** Lowest number, or lowest length in milliseconds for a time. */
    public @Nullable Long getMin() {
        return min;
    }

    /** Highest number, or highest length in milliseconds for a time, or longest text. */
    public @Nullable Long getMax() {
        return max;
    }

    /** Values worth trying. */
    public @NotNull List<String> getExamples() {
        return examples;
    }

    /** The list a choice setting is chosen from. */
    public @Nullable ChoiceSource getChoices() {
        return choices;
    }

    /** The value of this setting in {@code settings}, as the text an administrator would type. */
    public @NotNull String valueIn(@NotNull LibrarySettings settings) {
        return show.apply(read.apply(settings));
    }

    /** The default value as text. */
    public @NotNull String getDefault() {
        return valueIn(new LibrarySettings());
    }

    /** {@code value} (from {@link #valueIn} or {@link #getDefault}) as people read it: a switch says on or off in words. */
    public @NotNull String display(@NotNull String value) {
        return kind == SettingKind.SWITCH ? SettingTexts.switchWord("true".equals(value)) : value;
    }

    /**
     * {@code settings} with this setting changed to {@code text}.
     *
     * @throws SettingException when {@code text} is not acceptable
     */
    public @NotNull LibrarySettings apply(@NotNull LibrarySettings settings, @NotNull String text) {
        return write.apply(settings, parse.apply(text));
    }

    /**
     * {@code text} as it is shown after being accepted ({@code 1h30m} and {@code 90m} are the same time).
     *
     * @throws SettingException when {@code text} is not acceptable
     */
    public @NotNull String normalize(@NotNull String text) {
        return show.apply(parse.apply(text));
    }

    /** The range in words, or an empty text when the value has no range. */
    public @NotNull String range() {
        if (kind == SettingKind.NUMBER && min != null && max != null) {
            return "от " + min + " до " + max;
        }
        if (kind == SettingKind.TIME && min != null && max != null) {
            return "от " + SettingTexts.duration(min) + " до " + SettingTexts.duration(max);
        }
        if (kind == SettingKind.TEXT && max != null) {
            return "до " + max + " символов";
        }
        return "";
    }
}
