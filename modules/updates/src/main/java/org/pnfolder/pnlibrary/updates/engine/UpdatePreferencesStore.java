package org.pnfolder.pnlibrary.updates.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The administrator's update choices in one file: release channels, Java builds, automatic download and component pauses.
 *
 * <p>Several owners edit their own sections of the same file, so there is one in-memory model and one lock; every change
 * is read, modified and written back under that lock, and a save never discards what another owner stored.
 */
public final class UpdatePreferencesStore {
    private final Path file;
    private final Consumer<String> warning;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile Preferences preferences;

    public UpdatePreferencesStore(@NotNull Path file) {
        this(file, message -> {
        });
    }

    public UpdatePreferencesStore(@NotNull Path file, @NotNull Consumer<String> warning) {
        this.file = Objects.requireNonNull(file, "file");
        this.warning = Objects.requireNonNull(warning, "warning");
        this.preferences = load();
    }

    /** The file name, for messages. */
    @NotNull
    public String getFileName() {
        return file.getFileName().toString();
    }

    /** What is stored now. */
    @NotNull
    public Preferences current() {
        return preferences;
    }

    /**
     * Applies {@code change} to what is stored and saves the result; a failed save leaves the stored model as it was.
     *
     * @throws java.io.UncheckedIOException when the file cannot be written
     */
    public synchronized void update(@NotNull Function<Preferences, Preferences> change) {
        Preferences changed = change.apply(preferences);
        if (changed.equals(preferences)) {
            return;
        }
        write(changed);
        preferences = changed;
    }

    private Preferences load() {
        if (!Files.isRegularFile(file)) {
            return new Preferences();
        }
        try {
            JsonObject root = JsonParser.parseString(TextFiles.read(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement all = root.get("allPlugins");
            return new Preferences(
                section(root, "channels", JsonElement::getAsString),
                section(root, "automatic", JsonElement::getAsBoolean),
                all == null || all.isJsonNull() ? null : all.getAsBoolean(),
                section(root, "freezes", JsonElement::getAsLong),
                section(root, "javaBuilds", JsonElement::getAsInt));
        } catch (Exception error) {
            warning.accept("Unreadable update settings " + getFileName() + ": " + error.getClass().getSimpleName());
            return new Preferences();
        }
    }

    private static <T> Map<String, T> section(JsonObject root, String name, Function<JsonElement, T> value) {
        Map<String, T> result = new LinkedHashMap<>();
        JsonObject node = root.getAsJsonObject(name);
        if (node != null) {
            for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
                result.put(entry.getKey(), value.apply(entry.getValue()));
            }
        }
        return result;
    }

    private void write(Preferences value) {
        JsonObject json = new JsonObject();
        if (value.getAllPlugins() != null) {
            json.addProperty("allPlugins", value.getAllPlugins());
        }
        json.add("channels", sorted(value.getChannels()));
        json.add("automatic", sorted(value.getAutomatic()));
        json.add("freezes", sorted(value.getFreezes()));
        json.add("javaBuilds", sorted(value.getJavaBuilds()));
        try {
            AtomicFiles.write(file, gson.toJson(json).getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    private static JsonObject sorted(Map<String, ?> values) {
        JsonObject node = new JsonObject();
        for (Map.Entry<String, ?> entry : new TreeMap<>(values).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number) {
                node.addProperty(entry.getKey(), (Number) value);
            } else if (value instanceof Boolean) {
                node.addProperty(entry.getKey(), (Boolean) value);
            } else {
                node.addProperty(entry.getKey(), (String) value);
            }
        }
        return node;
    }

    /** What is stored: channel and automatic-download choices by product, the all-plugins choice and pause expiries in epoch millis. */
    public static final class Preferences {
        private final Map<String, String> channels;
        private final Map<String, Boolean> automatic;
        private final Boolean allPlugins;
        private final Map<String, Long> freezes;
        private final Map<String, Integer> javaBuilds;

        public Preferences() {
            this(Collections.emptyMap(), Collections.emptyMap(), null,
                Collections.emptyMap(), Collections.emptyMap());
        }

        public Preferences(@NotNull Map<String, String> channels, @NotNull Map<String, Boolean> automatic,
                           @Nullable Boolean allPlugins, @NotNull Map<String, Long> freezes,
                           @NotNull Map<String, Integer> javaBuilds) {
            this.channels = Collections.unmodifiableMap(new LinkedHashMap<>(channels));
            this.automatic = Collections.unmodifiableMap(new LinkedHashMap<>(automatic));
            this.allPlugins = allPlugins;
            this.freezes = Collections.unmodifiableMap(new LinkedHashMap<>(freezes));
            this.javaBuilds = Collections.unmodifiableMap(new LinkedHashMap<>(javaBuilds));
        }

        @NotNull
        public Map<String, String> getChannels() {
            return channels;
        }

        @NotNull
        public Map<String, Boolean> getAutomatic() {
            return automatic;
        }

        @Nullable
        public Boolean getAllPlugins() {
            return allPlugins;
        }

        @NotNull
        public Map<String, Long> getFreezes() {
            return freezes;
        }

        @NotNull
        public Map<String, Integer> getJavaBuilds() {
            return javaBuilds;
        }

        @NotNull
        public Preferences withChannels(@NotNull Map<String, String> value) {
            return new Preferences(value, automatic, allPlugins, freezes, javaBuilds);
        }

        @NotNull
        public Preferences withAutomatic(@NotNull Map<String, Boolean> value) {
            return new Preferences(channels, value, allPlugins, freezes, javaBuilds);
        }

        @NotNull
        public Preferences withAllPlugins(@Nullable Boolean value) {
            return new Preferences(channels, automatic, value, freezes, javaBuilds);
        }

        @NotNull
        public Preferences withFreezes(@NotNull Map<String, Long> value) {
            return new Preferences(channels, automatic, allPlugins, value, javaBuilds);
        }

        @NotNull
        public Preferences withJavaBuilds(@NotNull Map<String, Integer> value) {
            return new Preferences(channels, automatic, allPlugins, freezes, value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Preferences)) {
                return false;
            }
            Preferences that = (Preferences) other;
            return channels.equals(that.channels) && automatic.equals(that.automatic)
                && Objects.equals(allPlugins, that.allPlugins) && freezes.equals(that.freezes)
                && javaBuilds.equals(that.javaBuilds);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channels, automatic, allPlugins, freezes, javaBuilds);
        }

        @Override
        public String toString() {
            return "Preferences(channels=" + channels + ", automatic=" + automatic + ", allPlugins=" + allPlugins
                + ", freezes=" + freezes + ", javaBuilds=" + javaBuilds + ")";
        }
    }
}
