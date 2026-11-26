package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.backup.BackupExport;
import org.pnfolder.pnlibrary.api.updates.backup.BackupLabel;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateArtifact;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

/** Rewrites a module's update request so that it carries the supported API of the module's product descriptor. */
final class UpdateRequestBinding {
    private UpdateRequestBinding() {
    }

    /** The request with the supported API of {@code descriptor}; the request itself when there is nothing to bind. */
    static @Nullable PluginUpdateRequest bind(@Nullable PluginUpdateRequest source, @Nullable ProductDescriptor descriptor) {
        if (source == null || descriptor == null) {
            return source;
        }
        PluginUpdateRequest.Builder target = PluginUpdateRequest.builder()
                .repository(source.getRepositoryOwner(), source.getRepositoryName())
                .supportedApi(descriptor.getSupportedApi().getMinimum(), descriptor.getSupportedApi().getMaximum());
        copyArtifactRules(source, target);
        if (source.getConfigurationBackup() != null) {
            copyBackups(source.getConfigurationBackup(), target);
        }
        for (BackupExport export : source.getExports()) {
            target.backupExport(export.getKind(), export.getName(), export.getFileName(), export.getExport());
        }
        return target.build();
    }

    private static void copyBackups(UpdateConfigurationBackup source, PluginUpdateRequest.Builder target) {
        Map<BackupLabel, List<String>> byLabel = new LinkedHashMap<BackupLabel, List<String>>();
        for (String file : source.getFiles()) {
            BackupLabel label = source.labelOf(file);
            if (!byLabel.containsKey(label)) {
                byLabel.put(label, new ArrayList<String>());
            }
            byLabel.get(label).add(file);
        }
        for (Map.Entry<BackupLabel, List<String>> group : byLabel.entrySet()) {
            BackupLabel label = group.getKey();
            target.backup(label.getKind(), label.getName(), source.getRoot(), group.getValue().toArray(new String[0]));
        }
    }

    private static void copyArtifactRules(PluginUpdateRequest source, PluginUpdateRequest.Builder target) {
        for (PluginUpdateArtifact artifact : source.getArtifacts()) {
            if (artifact.getPlatform() == null) {
                target.artifact(artifact.getPattern(), artifact.getMinimumJava(), artifact.getMaximumJava());
            } else {
                target.artifact(artifact.getPattern(), artifact.getPlatform(), artifact.getMinimumJava(), artifact.getMaximumJava());
            }
        }
    }
}
