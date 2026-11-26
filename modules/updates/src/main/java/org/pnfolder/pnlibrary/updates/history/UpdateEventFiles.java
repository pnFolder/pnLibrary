package org.pnfolder.pnlibrary.updates.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

/** The files of a journal folder: which ones are entries and which of them are too old or too many. */
final class UpdateEventFiles {
    private UpdateEventFiles() {
    }

    /** The regular {@code .json} files in {@code directory}; temporary files are not entries. */
    @NotNull
    static List<Path> list(@NotNull Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (path.getFileName().toString().endsWith(".json")
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(path);
                }
            }
        }
        return files;
    }

    /**
     * Deletes the entries modified before {@code cutoffMillis} and every entry beyond the newest {@code maxEvents}.
     *
     * @return how many files were removed
     */
    static int prune(@NotNull Path directory, long cutoffMillis, int maxEvents) throws IOException {
        List<Path> files = list(directory);
        final List<Long> modified = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            modified.add(Files.getLastModifiedTime(files.get(index)).toMillis());
            order.add(index);
        }
        Collections.sort(order, (a, b) -> Long.compare(modified.get(b), modified.get(a)));
        int removed = 0;
        for (int position = 0; position < order.size(); position++) {
            int index = order.get(position);
            boolean surplus = position >= maxEvents || modified.get(index) < cutoffMillis;
            if (surplus && Files.deleteIfExists(files.get(index))) {
                removed++;
            }
        }
        return removed;
    }
}
