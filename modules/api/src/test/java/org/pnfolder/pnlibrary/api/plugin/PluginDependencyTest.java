package org.pnfolder.pnlibrary.api.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.Dependencies;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;

class PluginDependencyTest {
    @Test
    void unifiedFactoriesExposeManagedAndExternalVariants() {
        PluginDependency managed = Dependencies.managed("pnEconomy", "2.0.0", "pnFolder", "pnEconomy");
        assertNotNull(managed.getManaged());
        assertEquals("pneconomy", managed.getManaged().getProduct().getValue());

        PluginDependency external = Dependencies.plugin("Vault", "1.7.3", "https://example.org/vault");
        assertNotNull(external.getExternal());
        assertEquals("Vault", external.getExternal().getPlugin());
    }

    @Test
    void dependencyCanExplicitlyOverrideAutomaticDownloadPolicy() {
        StringBuilder sha256 = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            sha256.append("00");
        }
        PluginDependency dependency = Dependencies.plugin("OptionalPlugin", "1.0.0",
            "https://example.org/plugin.jar", sha256.toString(), false, DownloadPolicy.FORCED);
        assertEquals(DownloadPolicy.FORCED, dependency.getDownloadPolicy());
        assertEquals(DownloadPolicy.FORCED, dependency.getExternal().getDownloadPolicy());
    }
}
