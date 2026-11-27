package org.pnfolder.pnlibrary.updates.history.state;

import java.util.Locale;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;
import org.pnfolder.pnlibrary.common.io.TextFiles;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Persists the current update plan and a bounded history of earlier ones, quarantining corrupt state. */
public final class UpdateStateStore {
    // Gson cannot reflect into Instant on Java 17+ (module boundaries), so it is stored as the catalog's ISO-8601 text.
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) ->
            new JsonPrimitive(value.toString()))
        .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, type, context) ->
            Instant.parse(json.getAsString()))
        .create();
    private final int maximumEntries;
    private final long maximumBytes;
    private final Consumer<String> warning;
    private final Path stateFile;
    private final Path historyDirectory;

    /** Creates a store in {@code root} that keeps 100 earlier states of at most 10 MB in all. */
    public UpdateStateStore(@NotNull Path root) throws IOException {
        this(root, 100, 10L * 1024L * 1024L);
    }

    /** Creates a store that reports a quarantined state to {@code warning}. */
    public UpdateStateStore(@NotNull Path root, @NotNull Consumer<String> warning) throws IOException {
        this(root, 100, 10L * 1024L * 1024L, warning);
    }

    /** Creates a store with its own history limits. */
    public UpdateStateStore(@NotNull Path root, int maximumEntries, long maximumBytes) throws IOException {
        this(root, maximumEntries, maximumBytes, message -> {
        });
    }

    /** Creates a store with its own history limits that reports a quarantined state to {@code warning}. */
    public UpdateStateStore(@NotNull Path root, int maximumEntries, long maximumBytes,
                            @NotNull Consumer<String> warning) throws IOException {
        if (maximumEntries <= 0 || maximumBytes <= 0) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        this.maximumEntries = maximumEntries;
        this.maximumBytes = maximumBytes;
        this.warning = warning;
        this.stateFile = root.resolve("state.json");
        this.historyDirectory = root.resolve("history");
        Files.createDirectories(historyDirectory);
    }

    /** Writes {@code snapshot} as the current state and into the history, then trims the history. */
    public synchronized void save(@NotNull UpdatePlanSnapshot snapshot) throws IOException {
        byte[] bytes = UpdateStateJson.encode(snapshot, gson).getBytes(StandardCharsets.UTF_8);
        AtomicFiles.write(stateFile, bytes, true);
        AtomicFiles.write(historyDirectory.resolve(
            String.format(Locale.ROOT, "%020d-%s.json", snapshot.getRevision(), snapshot.getId())), bytes, true);
        prune();
    }

    /** The saved current state; an unreadable file is moved aside and null is returned. */
    @Nullable
    public UpdatePlanSnapshot current() throws IOException {
        return read(stateFile, true);
    }

    /** Earlier states, newest first; unreadable ones are skipped. */
    @NotNull
    public List<UpdatePlanSnapshot> history() throws IOException {
        List<UpdatePlanSnapshot> result = new ArrayList<>();
        for (Path file : historyFiles()) {
            UpdatePlanSnapshot snapshot = read(file, false);
            if (snapshot != null) {
                result.add(snapshot);
            }
        }
        return result;
    }

    @Nullable
    private UpdatePlanSnapshot read(Path path, boolean quarantine) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(TextFiles.read(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement plan = root.get("plan");
            JsonElement message = root.get("message");
            return new UpdatePlanSnapshot(UUID.fromString(root.get("id").getAsString()),
                root.get("revision").getAsLong(), decodeState(root.get("state").getAsString()),
                plan == null || plan.isJsonNull() ? null : gson.fromJson(plan, UpdatePlan.class),
                UpdateStateJson.decodeBlockers(root),
                message == null || message.isJsonNull() ? null : message.getAsString());
        } catch (Exception error) {
            if (quarantine) {
                quarantine(path, error);
            }
            return null;
        }
    }

    private void quarantine(Path path, Exception error) {
        Path target = path.resolveSibling(path.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        Failures.runCatchingNonFatal(() -> Files.move(path, target, StandardCopyOption.REPLACE_EXISTING))
            .onSuccess(moved -> warning.accept("Corrupt update state was quarantined: " + error.getMessage()))
            .onFailure(moveError -> warning.accept("Corrupt update state could not be quarantined ("
                + moveError.getMessage() + "): " + error.getMessage()));
    }

    /** Read historical names without exposing duplicate states in the public API. */
    private static UpdateState decodeState(String value) {
        switch (value) {
            case "CURRENT":
                return UpdateState.UP_TO_DATE;
            case "AVAILABLE":
                return UpdateState.UPDATE_AVAILABLE;
            case "DOWNLOADED":
                return UpdateState.UPDATE_STAGED;
            default:
                return UpdateState.valueOf(value);
        }
    }

    private void prune() throws IOException {
        // A history file that vanished or is locked (antivirus on Windows) is skipped; retention retries on the next
        // write. Failing here would fail a save whose state file is already written.
        List<Path> files = new ArrayList<>(historyFiles());
        long bytes = 0;
        for (Path file : files) {
            bytes += sizeOrZero(file);
        }
        while (files.size() > maximumEntries || bytes > maximumBytes) {
            Path oldest = files.remove(files.size() - 1);
            bytes -= sizeOrZero(oldest);
            Failures.runCatchingNonFatal(() -> Files.deleteIfExists(oldest))
                .onFailure(error -> warning.accept("Old update state " + oldest.getFileName()
                    + " could not be deleted: " + error.getClass().getSimpleName()));
        }
    }

    private static long sizeOrZero(Path file) {
        return Failures.runCatchingNonFatal(() -> Files.size(file)).getOrDefault(0L);
    }

    private List<Path> historyFiles() throws IOException {
        try (Stream<Path> stream = Files.list(historyDirectory)) {
            List<Path> files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            Collections.sort(files, Collections.reverseOrder());
            return files;
        }
    }
}
