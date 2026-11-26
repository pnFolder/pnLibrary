package org.pnfolder.pnlibrary.api.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.DependencyBuilder;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class DependencyBuilderTest {
    private static String repeat(String text, int times) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < times; i++) {
            result.append(text);
        }
        return result.toString();
    }

    @Test
    void externalPluginAcceptsADirectUrlWithoutManualIntegrityMetadata() {
        DependencyBuilder builder = new DependencyBuilder();
        builder.plugin("Vault", "1.7.3", it -> it.url("https://example.org/Vault.jar")
            .downloadPolicy(DownloadPolicy.AUTOMATIC));
        ExternalPluginDependency dependency = (ExternalPluginDependency) builder.build().get(0);

        assertEquals(SemanticVersion.parse("1.7.3"), dependency.getMinimumVersion());
        assertNull(dependency.getArtifact().getSha256());
        assertEquals(DownloadPolicy.AUTOMATIC, dependency.getDownloadPolicy());
    }

    @Test
    void managedProductAcceptsVersionsInsideItsDeclaredRange() {
        DependencyBuilder builder = new DependencyBuilder();
        builder.product("pneconomy", it -> it.minimumVersion("3.1.0")
            .maximumVersionExclusive("4.0.0")
            .github("pnFolder", "pnEconomy")
            .downloadPolicy(DownloadPolicy.AUTOMATIC));

        ManagedProductDependency dependency = (ManagedProductDependency) builder.build().get(0);
        assertTrue(dependency.getVersions().accepts(SemanticVersion.parse("3.1.0")));
        assertTrue(dependency.getVersions().accepts(SemanticVersion.parse("3.9.0")));
        assertFalse(dependency.getVersions().accepts(SemanticVersion.parse("4.0.0")));
    }

    @Test
    void manualDownloadPageCannotClaimAutomaticInstallation() {
        assertThrows(IllegalArgumentException.class, () -> new DependencyBuilder().plugin("LuckPerms",
            it -> it.minimumVersion("5.4.0")
                .downloadPage("https://luckperms.net/download")
                .downloadPolicy(DownloadPolicy.AUTOMATIC)));
    }

    @Test
    void exactExternalArtifactCanBeForced() {
        DependencyBuilder builder = new DependencyBuilder();
        builder.plugin("Legacy", it -> it.minimumVersion("2.0.0")
            .artifact("https://example.org/Legacy-2.0.0.jar", repeat("a", 64))
            .downloadPolicy(DownloadPolicy.FORCED));

        ExternalPluginDependency dependency = (ExternalPluginDependency) builder.build().get(0);
        assertNotNull(dependency.getArtifact());
        assertEquals(DownloadPolicy.FORCED, dependency.getDownloadPolicy());
    }

    @Test
    void duplicateProductIdsAreRejectedAfterNormalization() {
        DependencyBuilder builder = new DependencyBuilder();
        builder.product("PnEconomy", it -> it.minimumVersion("1.0.0").github("pnFolder", "one"));
        assertThrows(IllegalArgumentException.class,
            () -> builder.product("pneconomy", it -> it.minimumVersion("2.0.0").github("pnFolder", "two")));
    }
}
