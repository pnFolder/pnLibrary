package org.pnfolder.pnlibrary.core.observability.report;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry;
import org.pnfolder.pnlibrary.core.diagnostics.RegisteredDiagnosticConfiguration;
import org.pnfolder.pnlibrary.core.diagnostics.config.ConfigReader;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Adds the registered configuration files of a target to a support archive, exact when encrypted and redacted otherwise. */
final class ConfigurationArchiveSection {
    private final Path dataFolder;
    private final DiagnosticsRegistry diagnostics;
    private final ConfigReader reader;

    ConfigurationArchiveSection(Path dataFolder, DiagnosticsRegistry diagnostics, ConfigReader reader) {
        this.dataFolder = dataFolder;
        this.diagnostics = diagnostics;
        this.reader = reader;
    }

    void addTo(@NotNull SupportArchiveBuilder archive, @NotNull String target, boolean encrypted) {
        Set<String> used = new HashSet<>();
        for (RegisteredDiagnosticConfiguration registered : diagnostics.configurations(target)) {
            Path root = registered.getDataDirectory() != null ? registered.getDataDirectory() : dataFolder;
            String directory = "plugins/" + ArchivePaths.folderOf(registered.getPlugin()) + "/configuration/";
            if (encrypted) {
                addExact(archive, reader.readProtectedFile(registered.getConfiguration(), root), directory, used);
            } else {
                addRedacted(archive, reader.readRedactedFile(registered.getConfiguration(), root), directory, used);
            }
        }
    }

    private static void addExact(SupportArchiveBuilder archive, ConfigReader.ExactFile file, String directory,
                                 Set<String> used) {
        String path = ArchivePaths.unique(directory, ArchivePaths.safeConfigurationPath(file.getPath()), used);
        if (file.getError() == null) {
            archive.bytes(directory + path, file.getContent());
        } else {
            archive.text(directory + path + ".error.txt", file.getError() + "\n");
        }
    }

    private static void addRedacted(SupportArchiveBuilder archive, ConfigReader.RedactedFile file, String directory,
                                    Set<String> used) {
        String path = ArchivePaths.unique(directory, ArchivePaths.safeConfigurationPath(file.getPath()), used);
        if (file.getError() == null) {
            archive.text(directory + path, file.getContent());
        } else {
            archive.text(directory + path + ".error.txt", file.getError() + "\n");
        }
    }
}
