package org.pnfolder.pnlibrary.updates.service;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateArtifact;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata;

/** Checks that a registering plugin can be updated and finds the files the update pipeline works with. */
public final class RegistrationValidator {
    private static final String PAPER_REMAPPED = ".paper-remapped";

    private final PlatformOwnerMetadata ownership;
    private final PlatformIdentity identity;

    /** Creates a validator that reads plugin details from {@code ownership}. */
    public RegistrationValidator(@NotNull PlatformOwnerMetadata ownership, @NotNull PlatformIdentity identity) {
        this.ownership = ownership;
        this.identity = identity;
    }

    /** Returns the files of {@code owner} or throws when its names, version, location or request are not updatable. */
    @NotNull
    public ValidatedRegistration validate(@NotNull Object owner, @NotNull ProductDescriptor product,
                                          @NotNull PluginUpdateRequest request) {
        Map<String, String> info = ownership.ownerDetails(owner);
        String nativeProductName = info.get("name") != null ? info.get("name") : request.getRepositoryName();
        rejectReservedNames(nativeProductName, request.getRepositoryName());
        String version = info.get("version");
        if (version == null) {
            throw new IllegalStateException("Cannot determine the version of the registering plugin");
        }
        if (SemanticVersion.tryParse(version) == null) {
            throw new IllegalArgumentException("Version of " + nativeProductName + " must be semantic: " + version);
        }
        Path jar = jarOf(owner);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("Plugin must be loaded from a JAR file");
        }
        Path pluginsDirectory = pluginsDirectoryOf(jar);
        UpdateConfigurationBackup backup = request.getConfigurationBackup();
        if (backup != null && (backup.getRoot().equals(pluginsDirectory)
                || !backup.getRoot().toAbsolutePath().normalize().startsWith(pluginsDirectory))) {
            throw new IllegalArgumentException(
                    "Configuration backup root must be a component data directory inside the plugins directory");
        }
        PluginUpdateArtifact artifact = artifactOf(product, request);
        if (!product.getVersion().equals(SemanticVersion.parse(version))) {
            throw new IllegalArgumentException("Product version " + product.getVersion()
                    + " does not match native plugin version " + version);
        }
        return new ValidatedRegistration(jar, pluginsDirectory, artifact);
    }

    private void rejectReservedNames(String nativeProductName, String repositoryName) {
        for (String name : new String[] {nativeProductName, repositoryName}) {
            if (UpdateCommandWords.isReserved(name)) {
                throw new IllegalArgumentException("A plugin cannot be called '" + name + "' for updates: it is a word of the "
                        + "/pn update command (" + String.join(", ", UpdateCommandWords.RESERVED) + "). "
                        + "Rename the plugin or its repository.");
            }
        }
    }

    private Path jarOf(Object owner) {
        try {
            return Paths.get(owner.getClass().getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Cannot locate the JAR of the registering plugin", error);
        }
    }

    // Without own rules the Java range the product declares is enough: the catalogue says which file fits.
    private PluginUpdateArtifact artifactOf(ProductDescriptor product, PluginUpdateRequest request) {
        if (request.getArtifacts().isEmpty()) {
            return new PluginUpdateArtifact(".*", product.getMinimumJava(), product.getMaximumJava());
        }
        PluginUpdateArtifact artifact = request.artifactFor(JavaRuntime.getFeature(), identity.getType());
        if (artifact == null) {
            throw new IllegalStateException("No artifact of " + request.getRepositoryName()
                    + " is registered for Java " + JavaRuntime.getFeature());
        }
        return artifact;
    }

    /** Paper runs remapped copies from {@code plugins/.paper-remapped}; data folders and {@code update} still sit in {@code plugins}. */
    private static Path pluginsDirectoryOf(Path jar) {
        Path folder = jar.getParent();
        Path name = folder.getFileName();
        return name != null && name.toString().equals(PAPER_REMAPPED) ? folder.getParent() : folder;
    }
}
