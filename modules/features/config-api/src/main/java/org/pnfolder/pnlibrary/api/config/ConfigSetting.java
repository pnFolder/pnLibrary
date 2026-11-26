package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * One editable setting of a configuration file.
 *
 * <p>Create one with {@link #builder}; {@link #toBuilder()} derives a changed copy.
 */
public final class ConfigSetting {
    private final String path;
    private final String title;
    private final String description;
    private final ConfigSettingKind kind;
    private final String value;
    private final List<String> choices;
    private final Double min;
    private final Double max;
    private final ConfigApply apply;
    private final String unit;
    private final double step;
    private final TimeUnit duration;
    private final String format;
    private final List<String> examples;

    private ConfigSetting(Builder b) {
        this.path = b.path;
        this.title = b.title;
        this.description = b.description;
        this.kind = b.kind;
        this.value = b.value;
        this.choices = b.choices;
        this.min = b.min;
        this.max = b.max;
        this.apply = b.apply;
        this.unit = b.unit;
        this.step = b.step;
        this.duration = b.duration;
        this.format = b.format;
        this.examples = b.examples;
    }

    /** Starts a setting with the required values; everything else has a default. */
    @NotNull
    public static Builder builder(@NotNull String path, @NotNull String title, @NotNull String description,
                                  @NotNull ConfigSettingKind kind, @Nullable String value) {
        return new Builder(path, title, description, kind, value);
    }

    /** Creates a builder initialized with the values of this setting. */
    @NotNull
    public Builder toBuilder() {
        return new Builder(path, title, description, kind, value).choices(choices).min(min).max(max).apply(apply)
                .unit(unit).step(step).duration(duration).format(format).examples(examples);
    }

    /** Dotted YAML path, such as {@code shop.greeting}. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** Short name for a player. */
    @NotNull
    public String getTitle() {
        return title;
    }

    /** What it does. */
    @NotNull
    public String getDescription() {
        return description;
    }

    /** The kind of value. */
    @NotNull
    public ConfigSettingKind getKind() {
        return kind;
    }

    /** Current value as text, or {@code null} while the file is not loaded. */
    @Nullable
    public String getValue() {
        return value;
    }

    /** The allowed values of a {@link ConfigSettingKind#CHOICE}, empty otherwise. */
    @NotNull
    public List<String> getChoices() {
        return choices;
    }

    /** Lower bound of a number, or {@code null}. */
    @Nullable
    public Double getMin() {
        return min;
    }

    /** Upper bound of a number, or {@code null}. */
    @Nullable
    public Double getMax() {
        return max;
    }

    /** When a change starts to work. */
    @NotNull
    public ConfigApply getApply() {
        return apply;
    }

    /** What a number counts, empty when it counts nothing in particular. */
    @NotNull
    public String getUnit() {
        return unit;
    }

    /** The smallest change of one click on a number. */
    public double getStep() {
        return step;
    }

    /** The unit of a number that holds a length of time, or {@code null}. */
    @Nullable
    public TimeUnit getDuration() {
        return duration;
    }

    /** How a text is written, empty when free. */
    @NotNull
    public String getFormat() {
        return format;
    }

    /** Sample values of a text. */
    @NotNull
    public List<String> getExamples() {
        return examples;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigSetting)) {
            return false;
        }
        ConfigSetting that = (ConfigSetting) other;
        return Double.compare(step, that.step) == 0 && path.equals(that.path) && title.equals(that.title)
                && description.equals(that.description) && kind == that.kind && Objects.equals(value, that.value)
                && choices.equals(that.choices) && Objects.equals(min, that.min) && Objects.equals(max, that.max)
                && apply == that.apply && unit.equals(that.unit) && duration == that.duration
                && format.equals(that.format) && examples.equals(that.examples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, title, description, kind, value, choices, min, max, apply, unit, step, duration,
                format, examples);
    }

    @Override
    public String toString() {
        return "ConfigSetting(path=" + path + ", title=" + title + ", description=" + description + ", kind=" + kind
                + ", value=" + value + ", choices=" + choices + ", min=" + min + ", max=" + max + ", apply=" + apply
                + ", unit=" + unit + ", step=" + step + ", duration=" + duration + ", format=" + format
                + ", examples=" + examples + ")";
    }

    /** Mutable builder for {@link ConfigSetting}. */
    public static final class Builder {
        private final String path;
        private final String title;
        private final String description;
        private final ConfigSettingKind kind;
        private String value;
        private List<String> choices = Collections.emptyList();
        private Double min;
        private Double max;
        private ConfigApply apply = ConfigApply.LIVE;
        private String unit = "";
        private double step = 1.0;
        private TimeUnit duration;
        private String format = "";
        private List<String> examples = Collections.emptyList();

        Builder(String path, String title, String description, ConfigSettingKind kind, String value) {
            this.path = path;
            this.title = title;
            this.description = description;
            this.kind = kind;
            this.value = value;
        }

        /** Sets the current value as text. */
        @NotNull
        public Builder value(@Nullable String value) {
            this.value = value;
            return this;
        }

        /** Sets the allowed values of a choice. */
        @NotNull
        public Builder choices(@NotNull List<String> choices) {
            this.choices = choices;
            return this;
        }

        /** Sets the lower bound of a number. */
        @NotNull
        public Builder min(@Nullable Double min) {
            this.min = min;
            return this;
        }

        /** Sets the upper bound of a number. */
        @NotNull
        public Builder max(@Nullable Double max) {
            this.max = max;
            return this;
        }

        /** Sets when a change starts to work. */
        @NotNull
        public Builder apply(@NotNull ConfigApply apply) {
            this.apply = apply;
            return this;
        }

        /** Sets what a number counts. */
        @NotNull
        public Builder unit(@NotNull String unit) {
            this.unit = unit;
            return this;
        }

        /** Sets the smallest change of one click on a number. */
        @NotNull
        public Builder step(double step) {
            this.step = step;
            return this;
        }

        /** Sets the unit of a number that holds a length of time. */
        @NotNull
        public Builder duration(@Nullable TimeUnit duration) {
            this.duration = duration;
            return this;
        }

        /** Sets how a text is written. */
        @NotNull
        public Builder format(@NotNull String format) {
            this.format = format;
            return this;
        }

        /** Sets the sample values of a text. */
        @NotNull
        public Builder examples(@NotNull List<String> examples) {
            this.examples = examples;
            return this;
        }

        /** Creates the immutable setting. */
        @NotNull
        public ConfigSetting build() {
            return new ConfigSetting(this);
        }
    }
}
