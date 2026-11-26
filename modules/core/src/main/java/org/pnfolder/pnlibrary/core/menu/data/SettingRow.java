package org.pnfolder.pnlibrary.core.menu.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One setting of the library as the menu shows it. The kind is {@code SWITCH}, {@code NUMBER}, {@code TEXT},
 * {@code TIME} or {@code CHOICE}; the range is the allowed values in words, or empty; live says whether a change
 * starts to work at once, otherwise it waits for a restart; the choice source is the name of the list a
 * {@code CHOICE} setting is chosen from (the menu builds the list itself, it is not sent).
 */
public final class SettingRow {
    private final String key;
    private final String title;
    private final String kind;
    private final String description;
    private final String current;
    @SerializedName("default")
    private final String defaultValue;
    private final boolean changed;
    private final String range;
    private final List<String> examples;
    private final boolean live;
    private final String choiceSource;

    public SettingRow(@NotNull String key, @NotNull String title, @NotNull String kind, @NotNull String description,
                      @NotNull String current, @NotNull String defaultValue, boolean changed, @NotNull String range,
                      @NotNull List<String> examples, boolean live, @NotNull String choiceSource) {
        this.key = Objects.requireNonNull(key, "key");
        this.title = Objects.requireNonNull(title, "title");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.description = Objects.requireNonNull(description, "description");
        this.current = Objects.requireNonNull(current, "current");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.changed = changed;
        this.range = Objects.requireNonNull(range, "range");
        this.examples = Objects.requireNonNull(examples, "examples");
        this.live = live;
        this.choiceSource = Objects.requireNonNull(choiceSource, "choiceSource");
    }

    public SettingRow(@NotNull String key, @NotNull String title, @NotNull String kind, @NotNull String description,
                      @NotNull String current, @NotNull String defaultValue, boolean changed, @NotNull String range,
                      @NotNull List<String> examples, boolean live) {
        this(key, title, kind, description, current, defaultValue, changed, range, examples, live, "");
    }

    public @NotNull String getKey() {
        return key;
    }

    public @NotNull String getTitle() {
        return title;
    }

    public @NotNull String getKind() {
        return kind;
    }

    public @NotNull String getDescription() {
        return description;
    }

    public @NotNull String getCurrent() {
        return current;
    }

    public @NotNull String getDefaultValue() {
        return defaultValue;
    }

    public boolean isChanged() {
        return changed;
    }

    public @NotNull String getRange() {
        return range;
    }

    public @NotNull List<String> getExamples() {
        return examples;
    }

    public boolean isLive() {
        return live;
    }

    public @NotNull String getChoiceSource() {
        return choiceSource;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SettingRow)) {
            return false;
        }
        SettingRow that = (SettingRow) other;
        return key.equals(that.key) && title.equals(that.title) && kind.equals(that.kind)
            && description.equals(that.description) && current.equals(that.current)
            && defaultValue.equals(that.defaultValue) && changed == that.changed && range.equals(that.range)
            && examples.equals(that.examples) && live == that.live && choiceSource.equals(that.choiceSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, title, kind, description, current, defaultValue, changed, range, examples, live, choiceSource);
    }

    @Override
    public String toString() {
        return "SettingRow(key=" + key + ", current=" + current + ", default=" + defaultValue + ")";
    }
}
