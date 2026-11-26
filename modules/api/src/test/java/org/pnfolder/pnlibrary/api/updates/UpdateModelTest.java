package org.pnfolder.pnlibrary.api.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductDependency;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class UpdateModelTest {
    private static String zeros(int pairs) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pairs; i++) {
            result.append("00");
        }
        return result.toString();
    }

    @Test
    void channelsExpressTheMaximumAcceptedRisk() {
        assertTrue(UpdateChannel.STABLE.accepts(UpdateChannel.STABLE));
        assertFalse(UpdateChannel.STABLE.accepts(UpdateChannel.BETA));
        assertTrue(UpdateChannel.RC.accepts(UpdateChannel.RC));
        assertTrue(UpdateChannel.RC.accepts(UpdateChannel.STABLE));
        assertFalse(UpdateChannel.RC.accepts(UpdateChannel.BETA));
        assertTrue(UpdateChannel.BETA.accepts(UpdateChannel.STABLE));
        assertTrue(UpdateChannel.ALPHA.accepts(UpdateChannel.BETA));
        assertTrue(UpdateChannel.DEV.accepts(UpdateChannel.DEV));
    }

    @Test
    void componentIdentifiersAreNormalizedAndValidated() {
        assertEquals("pnmarket", ProductId.of("PnMarket").getValue());
        assertThrows(IllegalArgumentException.class, () -> ProductId.of("bad id"));
    }

    @Test
    void releaseValidatesApiAndDependencyMetadata() {
        ProductDependency dependency = new ProductDependency(ProductId.of("pneconomy"), SemanticVersion.parse("3.0.0"));
        ProductRelease release = new ProductRelease(ProductId.of("pnmarket"), SemanticVersion.parse("4.0.0"),
            UpdateChannel.STABLE, new ApiVersionRange(5, 5), null, Collections.singletonList(dependency), null,
            Collections.<ArtifactDescriptor>emptyList(), Collections.<ExternalPluginDependency>emptyList(), null,
            null, "");

        assertEquals(5, release.getSupportedApi().getMinimum());
        assertEquals("pneconomy", release.getDependencies().get(0).getProduct().getValue());
    }

    @Test
    void productDescriptorValidatesAndPreservesProductMetadata() {
        assertThrows(IllegalArgumentException.class,
            () -> ProductDescriptor.builder().version("3.4.0").pnLibraryApi(2, 1));
        ProductDescriptor descriptor = ProductDescriptor.builder().version("3.4.0")
            .pnLibraryApi(1, 2)
            .build()
            .bindTo("economy");
        assertEquals("economy", descriptor.getId().getValue());
        assertEquals("3.4.0", descriptor.getVersion().toString());
    }

    @Test
    void artifactDescriptorRejectsUnsafeFileAndMalformedDigest() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArtifactDescriptor("../economy.jar", PlatformType.BUKKIT, 8, null, 10L, zeros(32), null));
        assertThrows(IllegalArgumentException.class,
            () -> new ArtifactDescriptor("economy.jar", PlatformType.BUKKIT, 8, null, 10L, "00", null));
    }

    @Test
    void automaticExternalDependencyRequiresSecureCompleteArtifact() {
        assertThrows(IllegalArgumentException.class, () -> ExternalPluginDependency.builder("Vault", "1.7.3")
            .artifact("http://example.test/vault.jar", zeros(32))
            .build());
        ExternalPluginDependency manual = ExternalPluginDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases")
            .build();
        assertEquals("Vault", manual.getPlugin());
        assertNull(manual.getArtifact());
    }
}
