package org.pnfolder.pnlibrary.updates.paths;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore;

/**
 * Moves the state of older versions, which lay directly in the plugin folder, into {@code data}, and merges the two
 * old settings files into one. It never overwrites, never throws and reports what it could not do through the
 * warning consumer.
 */
public final class LegacyPathsMigration {
    private final LibraryPaths paths;
    private final Consumer<String> warning;

    /** Creates a migration that reports nothing. */
    public LegacyPathsMigration(@NotNull LibraryPaths paths) {
        this(paths, new Consumer<String>() {
            @Override
            public void accept(String message) {
            }
        });
    }

    /** Creates a migration that reports what it could not do to {@code warning}. */
    public LegacyPathsMigration(@NotNull LibraryPaths paths, @NotNull Consumer<String> warning) {
        this.paths = paths;
        this.warning = warning;
    }

    /** Moves every old location that exists and whose new place is still free. */
    public void run() {
        guarded("the library data", () -> {
            Path old = paths.getPluginFolder();
            move(old.resolve("updates"), paths.getUpdates());
            move(old.resolve("journal"), paths.getJournal());
            move(old.resolve("reports"), paths.getReports());
            move(old.resolve("observability"), paths.getObservability());
            move(old.resolve("diagnostics").resolve("history"), paths.getDiagnosticsHistory());
            removeIfEmpty(old.resolve("diagnostics"));
            move(old.resolve("task-history.tsv"), paths.getTaskHistory());
            move(old.resolve("event-history.tsv"), paths.getEventHistory());
            move(old.resolve("upload-ledger.json"), paths.getUploadLedger());
            move(old.resolve("downloads"), paths.getDownloads());
            mergeSettings();
        });
    }

    /** Moves {@code source} to {@code target} unless {@code target} exists; two folders are merged entry by entry. */
    private void move(Path source, Path target) {
        if (!Files.exists(source)) {
            return;
        }
        guarded(source.getFileName().toString(), () -> {
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                AtomicFiles.move(source, target, false);
            } else if (Files.isDirectory(source) && Files.isDirectory(target)) {
                for (Path entry : entries(source)) {
                    move(entry, target.resolve(entry.getFileName().toString()));
                }
                removeIfEmpty(source);
            }
        });
    }

    private static List<Path> entries(Path folder) throws IOException {
        List<Path> result = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                result.add(entry);
            }
        }
        return result;
    }

    private void removeIfEmpty(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try {
            Files.delete(folder);
        } catch (DirectoryNotEmptyException ignored) {
            // Something else lives there; it stays.
        } catch (Exception error) {
            warning.accept("Could not remove the empty legacy folder " + folder.getFileName() + ": "
                + error.getClass().getSimpleName());
        }
    }

    /** Folds the old {@code settings.json} and {@code freezes.json} into the preferences file, then removes them. */
    private void mergeSettings() {
        Path settings = paths.getUpdates().resolve("settings.json");
        Path freezes = paths.getUpdates().resolve("freezes.json");
        if (Files.exists(paths.getPreferences()) || !(Files.isRegularFile(settings) || Files.isRegularFile(freezes))) {
            return;
        }
        guarded("the update settings", () -> {
            UpdatePreferencesStore store = new UpdatePreferencesStore(paths.getPreferences(), warning);
            Legacy<OldSettings> merged = legacy(settings, OldSettings::new);
            Legacy<Map<String, Long>> paused = legacy(freezes, LegacyPathsMigration::readFreezes);
            store.update(current -> current.withChannels(merged.value.channels)
                .withAutomatic(merged.value.automatic).withAllPlugins(merged.value.allPlugins)
                .withFreezes(paused.value));
            if (merged.readable) {
                Files.deleteIfExists(settings);
            }
            if (paused.readable) {
                Files.deleteIfExists(freezes);
            }
        });
    }

    private static Map<String, Long> readFreezes(JsonObject root) {
        Map<String, Long> result = new LinkedHashMap<String, Long>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsLong());
        }
        return result;
    }

    /** Reads {@code file} with {@code read}; a missing file gives the empty value, an unreadable one is kept. */
    private <T> Legacy<T> legacy(Path file, Function<JsonObject, T> read) {
        T emptyValue = read.apply(new JsonObject());
        if (!Files.isRegularFile(file)) {
            return new Legacy<T>(emptyValue, true);
        }
        try {
            JsonObject root = JsonParser.parseString(TextFiles.read(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Legacy<T>(read.apply(root), true);
        } catch (Exception error) {
            warning.accept("Unreadable legacy file " + file.getFileName() + " was left in place: "
                + error.getClass().getSimpleName());
            return new Legacy<T>(emptyValue, false);
        }
    }

    private void guarded(String what, Action action) {
        try {
            action.run();
        } catch (Exception error) {
            warning.accept("Could not migrate " + what + " to the new layout: "
                + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private interface Action {
        void run() throws Exception;
    }

    private static final class Legacy<T> {
        private final T value;
        private final boolean readable;

        Legacy(T value, boolean readable) {
            this.value = value;
            this.readable = readable;
        }
    }

    private static final class OldSettings {
        private final Map<String, String> channels = new LinkedHashMap<String, String>();
        private final Map<String, Boolean> automatic = new LinkedHashMap<String, Boolean>();
        private final Boolean allPlugins;

        OldSettings(JsonObject root) {
            JsonObject channelNode = root.getAsJsonObject("channels");
            if (channelNode != null) {
                for (Map.Entry<String, JsonElement> entry : channelNode.entrySet()) {
                    channels.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            JsonObject automaticNode = root.getAsJsonObject("automatic");
            if (automaticNode != null) {
                for (Map.Entry<String, JsonElement> entry : automaticNode.entrySet()) {
                    automatic.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
            JsonElement all = root.get("allPlugins");
            allPlugins = all == null || all.isJsonNull() ? null : all.getAsBoolean();
        }
    }
}
