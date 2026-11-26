package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Remembers downloaded dependencies until a restart confirms that the server loaded them. */
public final class DownloadedDependencyStore {
    private final Path marker;

    /** Keeps the list in {@code downloads/dependencies.state} under {@code libraryData}. */
    public DownloadedDependencyStore(@NotNull Path libraryData) {
        this.marker = libraryData.resolve("downloads/dependencies.state");
    }

    /** The file that holds the names; it exists only while some are unconfirmed. */
    public @NotNull Path getMarker() {
        return marker;
    }

    /** Adds {@code dependencies} to the remembered names, ignoring the case. */
    public void record(@NotNull Collection<String> dependencies) throws IOException {
        List<String> recorded = readNames();
        for (String dependency : dependencies) {
            if (!containsIgnoreCase(recorded, dependency)) {
                recorded.add(dependency);
            }
        }
        Files.createDirectories(marker.getParent());
        Collections.sort(recorded, String.CASE_INSENSITIVE_ORDER);
        Files.write(marker, recorded);
    }

    /** Takes out of the list the names that {@code installed} now has, and returns them with their versions. */
    public @NotNull List<DownloadedDependency> consumeInstalled(@NotNull Map<String, String> installed)
        throws IOException {
        List<String> recorded = readNames();
        List<DownloadedDependency> confirmed = new ArrayList<DownloadedDependency>();
        for (String expected : recorded) {
            for (Map.Entry<String, String> entry : installed.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(expected)) {
                    confirmed.add(new DownloadedDependency(entry.getKey(), entry.getValue()));
                    break;
                }
            }
        }
        if (confirmed.isEmpty()) {
            return confirmed;
        }
        List<String> remaining = new ArrayList<String>();
        for (String expected : recorded) {
            if (!isConfirmed(confirmed, expected)) {
                remaining.add(expected);
            }
        }
        if (remaining.isEmpty()) {
            Files.deleteIfExists(marker);
        } else {
            Files.write(marker, remaining);
        }
        return confirmed;
    }

    private static boolean isConfirmed(List<DownloadedDependency> confirmed, String expected) {
        for (DownloadedDependency dependency : confirmed) {
            if (dependency.getName().equalsIgnoreCase(expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(List<String> names, String name) {
        for (String candidate : names) {
            if (candidate.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private List<String> readNames() throws IOException {
        List<String> names = new ArrayList<String>();
        if (!Files.exists(marker)) {
            return names;
        }
        for (String line : Files.readAllLines(marker)) {
            if (!line.trim().isEmpty()) {
                names.add(line.trim());
            }
        }
        return names;
    }
}
