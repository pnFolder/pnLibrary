package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalArtifact;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Picks, among a dependency's files, the one made for this server, and builds the download request for them. */
public final class DependencyDownloadRequestFactory {
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]+");

    private final Path serverUpdateFolder;
    private final PlatformType platform;
    private final Supplier<String> minecraft;

    /** The platform and the Minecraft version of this server pick the file of each dependency. */
    public DependencyDownloadRequestFactory(@NotNull Path serverUpdateFolder, @NotNull PlatformType platform,
                                            @NotNull Supplier<String> minecraft) {
        this.serverUpdateFolder = serverUpdateFolder;
        this.platform = platform;
        this.minecraft = minecraft;
    }

    /** The file of {@code dependency} meant for this server, or null when it has none (only a page, or no variant fits). */
    public @Nullable ExternalArtifact fileFor(@NotNull ExternalPluginDependency dependency) {
        return dependency.artifactFor(platform, minecraft.get());
    }

    /** The external dependencies that are not installed at a fitting version. */
    public @NotNull List<ExternalPluginDependency> missing(@NotNull List<? extends PluginDependency> dependencies,
                                                           @NotNull Map<String, String> installedPlugins) {
        List<ExternalPluginDependency> missing = new ArrayList<ExternalPluginDependency>();
        for (PluginDependency dependency : dependencies) {
            ExternalPluginDependency external = dependency.getExternal();
            if (external != null && !isAlreadyCompatible(external, installedPlugins)) {
                missing.add(external);
            }
        }
        return missing;
    }

    /** The request for the missing dependencies that have a file for this server, or null when there is none. */
    public @Nullable DependencyDownloadRequest create(@NotNull List<? extends PluginDependency> dependencies,
                                                      @NotNull Map<String, String> installedPlugins) {
        FileDownloads.Builder builder = FileDownloads.builder().dataDirectory(serverUpdateFolder);
        Map<String, ExternalPluginDependency> requirements = new LinkedHashMap<String, ExternalPluginDependency>();
        for (PluginDependency dependency : dependencies) {
            ExternalPluginDependency external = dependency.getExternal();
            if (external == null || isAlreadyCompatible(external, installedPlugins)) {
                continue;
            }
            ExternalArtifact artifact = fileFor(external);
            if (artifact == null) {
                continue;
            }
            String key = "dependency:" + external.getPlugin();
            builder.file(key, file -> declare(file, dependency, external, artifact));
            requirements.put(key, external);
        }
        if (requirements.isEmpty()) {
            return null;
        }
        return new DependencyDownloadRequest(builder.build(), requirements);
    }

    private void declare(FileDownload.Builder file, PluginDependency dependency, ExternalPluginDependency external,
                         ExternalArtifact artifact) {
        file.url(artifact.getUri().toString())
            .policy(dependency.getDownloadPolicy())
            .destination(DownloadDestination.DATA_FOLDER, safeFileName(external.getPlugin()));
        if (!dependency.getRequired()) {
            file.optional();
        }
        for (java.net.URI mirror : artifact.getMirrors()) {
            file.mirror(mirror.toString());
        }
        if (artifact.getSha256() != null) {
            file.sha256(artifact.getSha256());
        }
        if (artifact.getMaxBytes() != null) {
            file.maxBytes(artifact.getMaxBytes());
        }
    }

    private boolean isAlreadyCompatible(ExternalPluginDependency dependency, Map<String, String> installedPlugins) {
        for (Map.Entry<String, String> entry : installedPlugins.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(dependency.getPlugin())) {
                SemanticVersion version = SemanticVersion.tryParse(entry.getValue());
                return version != null && dependency.getVersions().accepts(version);
            }
        }
        return false;
    }

    private static String safeFileName(String plugin) {
        String name = UNSAFE.matcher(plugin).replaceAll("-");
        name = trimChars(name);
        return (name.trim().isEmpty() ? "plugin" : name) + ".jar";
    }

    private static String trimChars(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && (text.charAt(start) == '-' || text.charAt(start) == '.')) {
            start++;
        }
        while (end > start && (text.charAt(end - 1) == '-' || text.charAt(end - 1) == '.')) {
            end--;
        }
        return text.substring(start, end);
    }
}
