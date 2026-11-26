package org.pnfolder.pnlibrary.api.architecture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Finds the repository root from wherever Gradle runs the tests. */
final class ProjectRoot {
    private ProjectRoot() {
    }

    static Path find() {
        Path directory = Paths.get("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("settings.gradle.kts"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("settings.gradle.kts not found above " + Paths.get("").toAbsolutePath());
        }
        return directory;
    }
}
