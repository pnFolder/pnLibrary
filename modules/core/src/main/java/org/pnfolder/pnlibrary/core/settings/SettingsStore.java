package org.pnfolder.pnlibrary.core.settings;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * The values of the library's settings, kept in one file ({@code data/settings.json}).
 *
 * <p>Only what an administrator changed is written, so a default that is improved later reaches every server that
 * never touched it. A value that cannot be read (unknown key, wrong type, outside its limits) is ignored with a warning
 * and the default is used; a file that is not JSON at all is moved aside as {@code settings.json.broken}. Loading never
 * throws.
 */
public final class SettingsStore {
    private final Path file;
    private final Consumer<String> warning;
    private final com.google.gson.Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Consumer<LibrarySettings>> listeners = new CopyOnWriteArrayList<Consumer<LibrarySettings>>();
    /** Key to the value as text, for what was set; the single source of truth together with the registry. */
    private volatile Map<String, String> overrides;
    private volatile LibrarySettings settings;

    /** Opens the store; problems found while reading are dropped. */
    public SettingsStore(@NotNull Path file) {
        this(file, problem -> { });
    }

    /**
     * Opens the store.
     *
     * @param warning receives the problems found while reading, in words for the administrator
     */
    public SettingsStore(@NotNull Path file, @NotNull Consumer<String> warning) {
        this.file = file;
        this.warning = warning;
        this.overrides = load();
        this.settings = build(overrides);
    }

    /** The values in force now. */
    public @NotNull LibrarySettings current() {
        return settings;
    }

    /** The runtime policy built from the values in force now. */
    public @NotNull PnLibraryConfig config() {
        return settings.toConfig();
    }

    /** Every setting in registry order with its value now. */
    public @NotNull List<SettingValue> values() {
        List<SettingValue> values = new ArrayList<SettingValue>();
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            values.add(new SettingValue(spec, spec.valueIn(settings), overrides.containsKey(spec.getKey())));
        }
        return values;
    }

    /**
     * The setting named {@code key} with its value now.
     *
     * @throws SettingException when there is no such setting
     */
    public @NotNull SettingValue value(@NotNull String key) {
        SettingSpec spec = specOf(key);
        return new SettingValue(spec, spec.valueIn(settings), overrides.containsKey(key));
    }

    /** Calls {@code listener} with the new values after every change; it runs on the thread that changed them. */
    public void onChange(@NotNull Consumer<LibrarySettings> listener) {
        listeners.add(listener);
    }

    /**
     * Sets {@code key} to {@code text} and saves the file.
     *
     * @throws SettingException when the key is unknown, the value is not acceptable or the file cannot be written;
     *     nothing changes then
     */
    public synchronized @NotNull SettingChange set(@NotNull String key, @NotNull String text) {
        SettingSpec spec = specOf(key);
        Map<String, String> next = new LinkedHashMap<String, String>(overrides);
        next.put(key, spec.normalize(text));
        return commit(spec, next);
    }

    /**
     * Returns {@code key} to its default and saves the file.
     *
     * @throws SettingException when the key is unknown or the file cannot be written
     */
    public synchronized @NotNull SettingChange reset(@NotNull String key) {
        SettingSpec spec = specOf(key);
        Map<String, String> next = new LinkedHashMap<String, String>(overrides);
        next.remove(key);
        return commit(spec, next);
    }

    private SettingSpec specOf(String key) {
        SettingSpec spec = SettingsRegistry.get(key);
        if (spec == null) {
            throw new SettingException("Такой настройки нет: " + key + ". Список: /pn settings list");
        }
        return spec;
    }

    private SettingChange commit(SettingSpec spec, Map<String, String> next) {
        String before = spec.valueIn(settings);
        LibrarySettings built = build(next);
        save(next);
        overrides = next;
        settings = built;
        for (Consumer<LibrarySettings> listener : listeners) {
            Failures.runCatchingNonFatal(() -> {
                listener.accept(built);
                return null;
            }).onFailure(error -> warning.accept("Настройка " + spec.getKey()
                + " сохранена, но применить её сразу не вышло: " + error.getMessage()));
        }
        return new SettingChange(spec, before, spec.valueIn(built));
    }

    private LibrarySettings build(Map<String, String> values) {
        LibrarySettings built = new LibrarySettings();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            built = specOf(entry.getKey()).apply(built, entry.getValue());
        }
        return built;
    }

    private void save(Map<String, String> values) {
        JsonObject json = new JsonObject();
        for (SettingSpec spec : SettingsRegistry.getAll()) {
            String text = values.get(spec.getKey());
            if (text != null) {
                json.add(spec.getKey(), primitive(spec, text));
            }
        }
        try {
            AtomicFiles.write(file, gson.toJson(json).getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new SettingException("Не удалось сохранить " + file.getFileName() + ": " + error.getMessage());
        }
    }

    private static JsonPrimitive primitive(SettingSpec spec, String text) {
        switch (spec.getKind()) {
            case SWITCH:
                return new JsonPrimitive(Boolean.parseBoolean(text));
            case NUMBER:
                return new JsonPrimitive(Long.parseLong(text));
            default:
                return new JsonPrimitive(text);
        }
    }

    private Map<String, String> load() {
        Map<String, String> accepted = new LinkedHashMap<String, String>();
        if (!Files.isRegularFile(file)) {
            return accepted;
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(TextFiles.read(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception error) {
            moveAside();
            return accepted;
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            accept(accepted, entry.getKey(), entry.getValue());
        }
        return accepted;
    }

    private void accept(Map<String, String> accepted, String key, JsonElement element) {
        SettingSpec spec = SettingsRegistry.get(key);
        if (spec == null) {
            warning.accept(file.getFileName() + ": настройки «" + key + "» нет, значение пропущено.");
        } else if (!element.isJsonPrimitive()) {
            warning.accept(file.getFileName() + ": у настройки " + key + " не то значение, берётся "
                + spec.getDefault() + ".");
        } else {
            try {
                accepted.put(key, spec.normalize(element.getAsString()));
            } catch (SettingException error) {
                warning.accept(file.getFileName() + ": настройка " + key + ": " + error.getMessage()
                    + " Берётся значение по умолчанию: " + spec.getDefault() + ".");
            }
        }
    }

    private void moveAside() {
        Path broken = file.resolveSibling(file.getFileName() + ".broken");
        boolean moved = Failures.runCatchingNonFatal(
            () -> Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING)).isSuccess();
        warning.accept(moved
            ? file.getFileName() + " не читается, значения по умолчанию; файл сохранён как " + broken.getFileName() + "."
            : file.getFileName() + " не читается, значения по умолчанию.");
    }
}
