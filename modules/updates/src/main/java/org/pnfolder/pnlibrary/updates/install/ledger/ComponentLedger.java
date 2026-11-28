package org.pnfolder.pnlibrary.updates.install.ledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.updates.history.events.UpdateEventJournal;

/**
 * Remembers which managed components were present at the last start, so the history can say what appeared,
 * disappeared or changed between restarts, including changes made without the updater.
 *
 * <p>The first observation only records a baseline and writes nothing to the history.
 */
public final class ComponentLedger {
    private static final Type TYPE = new TypeToken<Map<String, Component>>() { }.getType();

    private final Path file;
    private final UpdateEventJournal journal;
    private final Consumer<String> warning;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** What the ledger remembers about one component. */
    public static final class Component {
        private final String version;
        private final String channel;
        private final boolean automaticDownload;

        /** Creates the remembered state of one component. */
        public Component(@NotNull String version, @NotNull String channel, boolean automaticDownload) {
            this.version = version;
            this.channel = channel;
            this.automaticDownload = automaticDownload;
        }

        /** The version at the last observation. */
        @NotNull
        public String getVersion() {
            return version;
        }

        /** The channel name, in lower case. */
        @NotNull
        public String getChannel() {
            return channel;
        }

        /** Whether automatic download was on. */
        public boolean getAutomaticDownload() {
            return automaticDownload;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Component)) {
                return false;
            }
            Component that = (Component) other;
            return automaticDownload == that.automaticDownload && Objects.equals(version, that.version)
                    && Objects.equals(channel, that.channel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(version, channel, automaticDownload);
        }

        @Override
        public String toString() {
            return "Component(version=" + version + ", channel=" + channel
                    + ", automaticDownload=" + automaticDownload + ")";
        }
    }

    /** Creates a ledger stored in {@code file} that reports unreadable state to {@code warning}. */
    public ComponentLedger(@NotNull Path file, @NotNull UpdateEventJournal journal, @NotNull Consumer<String> warning) {
        this.file = Objects.requireNonNull(file, "file");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.warning = Objects.requireNonNull(warning, "warning");
    }

    /** Creates a ledger that ignores warnings. */
    public ComponentLedger(@NotNull Path file, @NotNull UpdateEventJournal journal) {
        this(file, journal, message -> { });
    }

    /** Compares {@code current} with the remembered state; see {@link #observe(Map, Set)}. */
    public void observe(@NotNull Map<String, Component> current) throws IOException {
        observe(current, java.util.Collections.emptySet());
    }

    /**
     * Compares {@code current} with the remembered state, records what differs and stores {@code current}.
     *
     * @param current every managed component that is registered now, by product ID
     * @param verifiedByHealth products whose version change was already reported by a post-restart health check
     */
    public synchronized void observe(@NotNull Map<String, Component> current, @NotNull Set<String> verifiedByHealth)
            throws IOException {
        Map<String, Component> previous = load();
        if (previous != null) {
            for (Map.Entry<String, Component> entry : new TreeMap<>(current).entrySet()) {
                recordChanges(entry.getKey(), previous.get(entry.getKey()), entry.getValue(), verifiedByHealth);
            }
            for (Map.Entry<String, Component> entry : new TreeMap<>(previous).entrySet()) {
                if (!current.containsKey(entry.getKey())) {
                    Component before = entry.getValue();
                    journal.recordComponent(ComponentOperation.REMOVED, entry.getKey(), before.version, before.version, null);
                }
            }
        }
        save(current);
    }

    /**
     * Stores the settings just changed by an administrator, so the next start does not report the same change
     * again. Without a baseline there is nothing to update; the first observation will capture them.
     */
    public synchronized void updateSettings(@NotNull String product, @NotNull String channel, boolean automaticDownload)
            throws IOException {
        Map<String, Component> loaded = load();
        if (loaded == null) {
            return;
        }
        Map<String, Component> known = new HashMap<>(loaded);
        Component before = known.get(product);
        if (before == null) {
            return;
        }
        known.put(product, new Component(before.version, channel, automaticDownload));
        save(known);
    }

    private void recordChanges(String product, Component before, Component now, Set<String> verifiedByHealth)
            throws IOException {
        if (before == null) {
            journal.recordComponent(ComponentOperation.ADDED, product, null, now.version, null);
            return;
        }
        if (!before.version.equals(now.version) && !verifiedByHealth.contains(product)) {
            journal.recordComponent(ComponentOperation.VERSION_CHANGED, product, before.version, now.version, null);
        }
        String message = describeSettings(before, now);
        if (message != null) {
            journal.recordComponent(ComponentOperation.SETTINGS_CHANGED, product, now.version, now.version, message);
        }
    }

    @Nullable
    private String describeSettings(Component before, Component now) {
        List<String> changes = new ArrayList<>();
        if (!before.channel.equals(now.channel)) {
            changes.add("канал: " + before.channel + " → " + now.channel);
        }
        if (before.automaticDownload != now.automaticDownload) {
            changes.add("автозагрузка: " + (before.automaticDownload ? "вкл" : "выкл") + " → "
                    + (now.automaticDownload ? "вкл" : "выкл"));
        }
        return changes.isEmpty() ? null : String.join("; ", changes);
    }

    @Nullable
    private Map<String, Component> load() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return gson.fromJson(TextFiles.read(file, StandardCharsets.UTF_8), TYPE);
        } catch (Exception error) {
            warning.accept("Unreadable component ledger " + file.getFileName() + ": " + error.getClass().getSimpleName());
            return null;
        }
    }

    private void save(Map<String, Component> components) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".components-", ".tmp");
        try {
            TextFiles.write(temporary, gson.toJson(new TreeMap<>(components), TYPE), StandardCharsets.UTF_8);
            AtomicFiles.move(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
