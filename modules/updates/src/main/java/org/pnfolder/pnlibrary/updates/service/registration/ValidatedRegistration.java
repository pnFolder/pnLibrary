package org.pnfolder.pnlibrary.updates.service.registration;

import java.nio.file.Path;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateArtifact;

/** What a plugin must provide on disk to be updated: its jar, the plugins directory it sits in and its artifact. */
public final class ValidatedRegistration {
    private final Path jar;
    private final Path pluginsDirectory;
    private final PluginUpdateArtifact artifact;

    /** Creates the result of a successful validation. */
    public ValidatedRegistration(@NotNull Path jar, @NotNull Path pluginsDirectory, @NotNull PluginUpdateArtifact artifact) {
        this.jar = Objects.requireNonNull(jar, "jar");
        this.pluginsDirectory = Objects.requireNonNull(pluginsDirectory, "pluginsDirectory");
        this.artifact = Objects.requireNonNull(artifact, "artifact");
    }

    /** The jar the plugin runs from. */
    @NotNull
    public Path getJar() {
        return jar;
    }

    /** The plugins directory the jar sits in. */
    @NotNull
    public Path getPluginsDirectory() {
        return pluginsDirectory;
    }

    /** The artifact rule that fits the running Java. */
    @NotNull
    public PluginUpdateArtifact getArtifact() {
        return artifact;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ValidatedRegistration)) {
            return false;
        }
        ValidatedRegistration that = (ValidatedRegistration) other;
        return jar.equals(that.jar) && pluginsDirectory.equals(that.pluginsDirectory) && artifact.equals(that.artifact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jar, pluginsDirectory, artifact);
    }

    @Override
    public String toString() {
        return "ValidatedRegistration(jar=" + jar + ", pluginsDirectory=" + pluginsDirectory + ", artifact=" + artifact + ")";
    }
}
