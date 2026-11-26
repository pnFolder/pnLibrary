package org.pnfolder.pnlibrary.core.downloads.fetch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.downloads.FileDownload;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Checks that a downloaded plugin JAR is the plugin and version a dependency asked for. */
public final class PluginJarVerifier {
    private static final class Descriptor {
        final List<String> names;
        final String version;

        Descriptor(List<String> names, String version) {
            this.names = names;
            this.version = version;
        }
    }

    private final PlatformIdentity platform;

    /** Creates a verifier that reads the descriptor file of {@code platform}. */
    public PluginJarVerifier(@NotNull PlatformIdentity platform) {
        this.platform = platform;
    }

    /** Throws {@link IllegalArgumentException} when the JAR at {@code path} does not meet {@code expected}. */
    public void verify(@NotNull Path path, @NotNull ExternalPluginDependency expected) throws IOException {
        Descriptor descriptor = readDescriptor(path);
        verifyPluginIdentity(descriptor, expected);
        verifyPluginVersion(descriptor, expected);
    }

    /** Adapts this verifier to a {@link DownloadVerifier} for one dependency. */
    public @NotNull DownloadVerifier forDependency(@NotNull ExternalPluginDependency expected) {
        return (FileDownload declaration, Path file) -> verify(file, expected);
    }

    @SuppressWarnings("unchecked")
    private Descriptor readDescriptor(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry entry = jar.getJarEntry(platform.getDescriptorFileName());
            if (entry == null) {
                throw new IllegalArgumentException("JAR плагина не содержит поддерживаемого descriptor");
            }
            Object loaded;
            try (InputStream input = jar.getInputStream(entry)) {
                loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
            }
            if (!(loaded instanceof Map)) {
                throw new IllegalArgumentException("descriptor плагина повреждён");
            }
            Map<String, Object> values = (Map<String, Object>) loaded;
            List<String> names = new ArrayList<String>();
            addText(names, values.get("id"));
            addText(names, values.get("name"));
            Object version = values.get("version");
            if (version == null) {
                throw new IllegalArgumentException("descriptor плагина не содержит version");
            }
            return new Descriptor(names, version.toString());
        }
    }

    private static void addText(List<String> names, Object value) {
        if (value != null) {
            names.add(value.toString());
        }
    }

    private void verifyPluginIdentity(Descriptor descriptor, ExternalPluginDependency expected) {
        if (!expected.getVerifyPluginId()) {
            return;
        }
        for (String name : descriptor.names) {
            if (name.equalsIgnoreCase(expected.getPlugin())) {
                return;
            }
        }
        throw new IllegalArgumentException("скачанный JAR объявляет " + String.join("/", descriptor.names)
            + ", ожидался " + expected.getPlugin());
    }

    private void verifyPluginVersion(Descriptor descriptor, ExternalPluginDependency expected) {
        if (!expected.getVerifyVersion()) {
            return;
        }
        SemanticVersion version = SemanticVersion.tryParse(descriptor.version);
        if (version == null) {
            throw new IllegalArgumentException(
                "версия скачанного " + expected.getPlugin() + " не распознана: " + descriptor.version);
        }
        if (!expected.getVersions().accepts(version)) {
            throw new IllegalArgumentException("версия скачанного " + expected.getPlugin() + " " + version
                + " не соответствует требованию " + expected.getMinimumVersion());
        }
    }
}
