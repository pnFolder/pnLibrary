package org.pnfolder.pnlibrary.core.journal.store;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Segment files are named {@code <level>-<yyyy-MM-dd>[-segment].pnj}. */
public final class JournalFiles {
    private final Path directory;
    // Each listing costs a system call per file, so callers keep it rare.
    private final AtomicInteger listings = new AtomicInteger();

    public JournalFiles(@NotNull Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public @NotNull AtomicInteger getListings() {
        return listings;
    }

    public @NotNull List<Path> all() {
        if (!Files.isDirectory(directory)) {
            return Collections.emptyList();
        }
        listings.incrementAndGet();
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".pnj"))
                .sorted().collect(Collectors.toList());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public @NotNull List<Path> of(@NotNull JournalLevel level, @NotNull List<Path> files) {
        String prefix = level.getFileGroup() + "-";
        List<Path> matching = new ArrayList<>();
        for (Path file : files) {
            if (file.getFileName().toString().startsWith(prefix)) {
                matching.add(file);
            }
        }
        return matching;
    }

    public @Nullable JournalLevel levelOf(@NotNull Path file) {
        for (JournalLevel level : JournalLevel.values()) {
            if (file.getFileName().toString().startsWith(level.getFileGroup() + "-")) {
                return level;
            }
        }
        return null;
    }

    public @NotNull Path pathOf(@NotNull JournalLevel level, @NotNull LocalDate date, int segment) {
        return directory.resolve(level.getFileGroup() + "-" + date + (segment == 0 ? "" : "-" + segment) + ".pnj");
    }

    public @Nullable LocalDate dateOf(@NotNull Path file) {
        return Failures.runCatchingNonFatal(() -> LocalDate.parse(afterFirstDash(file).substring(0, 10))).getOrNull();
    }

    public int segmentOf(@NotNull Path file) {
        String rest = afterFirstDash(file);
        rest = rest.length() > 10 ? rest.substring(10) : "";
        if (rest.startsWith("-")) {
            rest = rest.substring(1);
        }
        if (rest.endsWith(".pnj")) {
            rest = rest.substring(0, rest.length() - 4);
        }
        try {
            return Integer.parseInt(rest);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    private static String afterFirstDash(Path file) {
        String name = file.getFileName().toString();
        int dash = name.indexOf('-');
        return dash < 0 ? name : name.substring(dash + 1);
    }
}
