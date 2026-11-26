package org.pnfolder.pnlibrary.core.plugin.module;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

/**
 * Which modules an administrator keeps switched off across restarts, saved as {@code modules/preferences.json}
 * ({@code {"disabled": ["plugin:module"]}}). Without a file the choices live only in memory.
 */
public final class ModulePreferences {
    private final Path file;
    private final Set<String> disabled = new LinkedHashSet<>();

    public ModulePreferences() {
        this(null);
    }

    public ModulePreferences(@Nullable Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            read(file);
        }
    }

    private void read(Path path) {
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            JsonObject root = new Gson().fromJson(text, JsonObject.class);
            if (root != null && root.has("disabled")) {
                for (JsonElement element : root.getAsJsonArray("disabled")) {
                    disabled.add(element.getAsString());
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // a broken file means no saved choices: every module starts
        }
    }

    /** Whether the module starts with the server. */
    public synchronized boolean autoStart(@NotNull String key) {
        return !disabled.contains(key);
    }

    /** Remembers the choice and writes it to disk. */
    public synchronized void setAutoStart(@NotNull String key, boolean enabled) {
        boolean changed = enabled ? disabled.remove(key) : disabled.add(key);
        if (!changed || file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.add("disabled", new Gson().toJsonTree(new ArrayList<>(disabled)));
            AtomicFiles.write(file, new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
