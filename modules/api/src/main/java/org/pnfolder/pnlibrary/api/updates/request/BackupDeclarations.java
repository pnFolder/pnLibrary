package org.pnfolder.pnlibrary.api.updates.request;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.pnfolder.pnlibrary.api.updates.backup.BackupExport;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;
import org.pnfolder.pnlibrary.api.updates.backup.BackupLabel;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;

/** What a plugin asked to keep before an update: labelled files of one data directory and its own exports. */
final class BackupDeclarations {
    private UpdateConfigurationBackup files;
    private final List<BackupExport> exports = new ArrayList<BackupExport>();

    UpdateConfigurationBackup files() {
        return files;
    }

    List<BackupExport> exports() {
        return Collections.unmodifiableList(new ArrayList<BackupExport>(exports));
    }

    void addExport(BackupKind kind, String name, String fileName, BackupExport.Writer writer) {
        for (BackupExport existing : exports) {
            if (existing.getFileName().equals(fileName)) {
                throw new IllegalArgumentException("Export file names must differ");
            }
        }
        exports.add(new BackupExport(kind, name, fileName, writer));
    }

    void addFiles(BackupKind kind, String name, Path dataDirectory, String[] names) {
        BackupLabel label = new BackupLabel(kind, name);
        Map<String, BackupLabel> labels = new LinkedHashMap<String, BackupLabel>();
        for (String file : names) {
            labels.put(file, label);
        }
        UpdateConfigurationBackup added = new UpdateConfigurationBackup(dataDirectory, Arrays.asList(names), labels);
        if (files == null) {
            files = added;
            return;
        }
        if (!files.getRoot().equals(added.getRoot())) {
            throw new IllegalArgumentException("All backups of one component must use the same data directory");
        }
        List<String> merged = new ArrayList<String>(files.getFiles());
        merged.addAll(added.getFiles());
        Map<String, BackupLabel> mergedLabels = new LinkedHashMap<String, BackupLabel>(files.getLabels());
        mergedLabels.putAll(added.getLabels());
        files = new UpdateConfigurationBackup(files.getRoot(), merged, mergedLabels);
    }
}
