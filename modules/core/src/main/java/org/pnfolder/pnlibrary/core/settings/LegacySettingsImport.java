package org.pnfolder.pnlibrary.core.settings;

import java.util.Objects;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Takes the values of the old {@code config.yml} and {@code downloads.yml} (and the download choice kept in
 * {@code downloads/preferences.state}) into the {@link SettingsStore} once, then renames the files to
 * {@code *.yml.old} so nothing is imported twice.
 *
 * <p>A key that the store already holds is never replaced: what an administrator set with a command wins over an old
 * file. It never throws; what could not be read is reported through the warning consumer and the file is left where
 * it is.
 */
public final class LegacySettingsImport {
    private final LibraryPaths paths;
    private final Consumer<String> warning;

    /** Creates an import that drops its warnings. */
    public LegacySettingsImport(@NotNull LibraryPaths paths) {
        this(paths, text -> { });
    }

    /** Creates an import; {@code warning} receives what could not be imported, in words for the administrator. */
    public LegacySettingsImport(@NotNull LibraryPaths paths, @NotNull Consumer<String> warning) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.warning = Objects.requireNonNull(warning, "warning");
    }

    /** Imports into {@code store} every old file that exists. */
    public void run(@NotNull SettingsStore store) {
        Set<String> held = new HashSet<>();
        for (SettingValue value : store.values()) {
            if (value.getChanged()) {
                held.add(value.getSpec().getKey());
            }
        }
        importYaml(paths.getLegacyConfig(), "", store, held);
        importYaml(paths.getLegacyDownloadsConfig(), "downloads.", store, held);
        importDownloadChoice(store, held);
    }

    private void importYaml(Path file, String prefix, SettingsStore store, Set<String> held) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Map<?, ?> root;
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(TextFiles.read(file));
            root = loaded instanceof Map ? (Map<?, ?>) loaded : new LinkedHashMap<>();
        } catch (Exception error) {
            warning.accept(file.getFileName() + " не прочитан и остался на месте: " + firstLine(error.getMessage()));
            return;
        }
        for (Map.Entry<String, Object> entry : flatten(root, prefix).entrySet()) {
            take(store, held, file.getFileName().toString(), entry.getKey(), entry.getValue());
        }
        retire(file);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "null";
        }
        return message.split("\r\n|\r|\n", -1)[0];
    }

    /** {@code destinations: {cache: true}} becomes {@code destinations.cache}; keys of the old files were already kebab case. */
    private Map<String, Object> flatten(Map<?, ?> map, String prefix) {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flat.putAll(flatten((Map<?, ?>) entry.getValue(), key + "."));
            } else {
                flat.put(key, entry.getValue());
            }
        }
        return flat;
    }

    private void take(SettingsStore store, Set<String> held, String source, String key, Object value) {
        SettingSpec spec = SettingsRegistry.get(key);
        if (spec == null && SettingsRegistry.isRetired(key)) {
            return;
        }
        if (spec == null) {
            warning.accept(source + ": параметр «" + key + "» больше не существует, пропущен.");
            return;
        }
        if (held.contains(key) || value == null) {
            return;
        }
        try {
            String text = value.toString();
            if (!spec.normalize(text).equals(spec.getDefault())) {
                store.set(key, text);
            }
        } catch (SettingException error) {
            warning.accept(source + ": " + spec.getKey() + " не перенесён: " + error.getMessage());
        }
    }

    /** The old {@code /pn deps auto} choice beat {@code downloads.yml}, so it beats it here too. */
    private void importDownloadChoice(SettingsStore store, Set<String> held) {
        Path file = paths.getDownloads().resolve("preferences.state");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Boolean choice = Failures.runCatchingNonFatal(() -> readChoice(file)).getOrNull();
        if (choice != null && !held.contains("downloads.automatic")) {
            Failures.runCatchingNonFatal(() -> store.set("downloads.automatic", choice.toString()))
                .onFailure(error -> warning.accept("Выбор автозагрузки не перенесён: " + error.getMessage()));
        }
        Failures.runCatchingNonFatal(() -> Files.deleteIfExists(file));
    }

    private static Boolean readChoice(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        for (String line : lines) {
            if (line.startsWith("automatic=")) {
                String value = line.substring(line.indexOf('=') + 1).trim();
                return "true".equals(value) ? Boolean.TRUE : "false".equals(value) ? Boolean.FALSE : null;
            }
        }
        return null;
    }

    private void retire(Path file) {
        Path old = file.resolveSibling(file.getFileName() + ".old");
        Failures.runCatchingNonFatal(() -> {
            AtomicFiles.move(file, old);
            return null;
        }).fold(ignored -> {
            warning.accept(file.getFileName() + " больше не используется: настройки перенесены в data/settings.json "
                + "и меняются командой /pn settings. Файл сохранён как " + old.getFileName() + ".");
            return null;
        }, error -> {
            warning.accept(file.getFileName() + " не удалось переименовать в " + old.getFileName() + ": "
                + error.getMessage());
            return null;
        });
    }
}
