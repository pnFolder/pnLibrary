package org.pnfolder.pnlibrary.core.downloads.dependencies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

class DependencyDownloadRequestFactoryTest {
    @TempDir
    Path directory;

    private DependencyDownloadRequestFactory factory(PlatformType platform, String minecraft) {
        return new DependencyDownloadRequestFactory(directory.resolve("update"), platform, () -> minecraft);
    }

    @Test
    void createsASafeRequestOnlyForMissingAutomaticDependencies() {
        ExternalPluginDependency dependency = ExternalPluginDependency.builder("Unsafe Plugin!", "1.2.0")
            .url("https://example.org/plugin.jar")
            .downloadPolicy(DownloadPolicy.AUTOMATIC)
            .build();

        DependencyDownloadRequest plan = factory(PlatformType.BUKKIT, "1.21.4")
            .create(Arrays.<PluginDependency>asList(dependency), Collections.<String, String>emptyMap());

        assertNotNull(plan);
        assertEquals(directory.resolve("update"), plan.getRequest().getDataDirectory());
        assertEquals("Unsafe-Plugin.jar", plan.getRequest().getFiles().get(0).getRelativePath());
        assertEquals(dependency.getExternal(), plan.getRequirements().get("dependency:Unsafe Plugin!"));
    }

    @Test
    void doesNotRequestAnAlreadyCompatibleDependency() {
        ExternalPluginDependency dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .url("https://example.org/Vault.jar")
            .downloadPolicy(DownloadPolicy.AUTOMATIC)
            .build();

        assertNull(factory(PlatformType.BUKKIT, "1.21.4").create(
            Arrays.<PluginDependency>asList(dependency), Collections.singletonMap("vault", "1.7.3")));
    }

    @Test
    void downloadsTheFileMadeForThisServerAndChecksOnlyItsChecksum() {
        String old = repeat('a');
        String modern = repeat('b');
        ExternalPluginDependency dependency = ExternalPluginDependency.builder("Vault", "1.7.0")
            .variant("https://example.org/Vault-old.jar", old, Collections.<PlatformType>emptySet(), null, "1.20",
                Collections.<String>emptyList(), null, null)
            .variant("https://example.org/Vault-proxy.jar", repeat('c'),
                new HashSet<PlatformType>(Arrays.asList(PlatformType.VELOCITY)), null, null,
                Collections.<String>emptyList(), null, null)
            .variant("https://example.org/Vault-new.jar", modern, Collections.<PlatformType>emptySet(), "1.20", null,
                Collections.<String>emptyList(), "для 1.20 и новее", null)
            .build();

        DependencyDownloadRequest plan = factory(PlatformType.BUKKIT, "1.21.4")
            .create(Arrays.<PluginDependency>asList(dependency), Collections.<String, String>emptyMap());
        assertNotNull(plan);
        assertEquals("https://example.org/Vault-new.jar",
            plan.getRequest().getFiles().get(0).getSource().getUri().toString());
        assertEquals(modern, plan.getRequest().getFiles().get(0).getSource().getSha256());

        assertEquals(old, factory(PlatformType.BUKKIT, "1.16.5").fileFor(dependency).getSha256());
        assertNull(factory(PlatformType.BUNGEECORD, null).fileFor(dependency));
    }

    @Test
    void aFileMayCarryItsOwnSizeLimit() {
        ExternalPluginDependency dependency = ExternalPluginDependency.builder("Vault", "1.7.0")
            .url("https://example.org/Vault.jar")
            .maxBytes(2_000_000)
            .build();

        DependencyDownloadRequest plan = factory(PlatformType.BUKKIT, null)
            .create(Arrays.<PluginDependency>asList(dependency), Collections.<String, String>emptyMap());

        assertNotNull(plan);
        assertEquals(Long.valueOf(2_000_000L), plan.getRequest().getFiles().get(0).getSource().getMaxBytes());
    }

    private static String repeat(char symbol) {
        char[] chars = new char[64];
        Arrays.fill(chars, symbol);
        return new String(chars);
    }
}
