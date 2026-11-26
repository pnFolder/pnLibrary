package org.pnfolder.pnlibrary.updates.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

class ReleaseChannelSelectorTest {
    private final ProductId product = ProductId.of("example");

    @Test
    void usesConfiguredChannelInsteadOfRegistrationDefault() {
        List<ProductRelease> releases = Arrays.asList(
            release("2.0.0", UpdateChannel.STABLE),
            release("3.0.0-dev.1", UpdateChannel.DEV));

        ReleaseChannelSelection stable = ReleaseChannelSelector.select(product, releases, UpdateChannel.STABLE);
        ReleaseChannelSelection development = ReleaseChannelSelector.select(product, releases, UpdateChannel.DEV);

        assertEquals("2.0.0", stable.getLatestAllowed().getVersion().toString());
        assertEquals("3.0.0-dev.1", development.getLatestAllowed().getVersion().toString());
        assertEquals(releases, development.getAll());
    }

    private ProductRelease release(String version, UpdateChannel channel) {
        return new ProductRelease(product, SemanticVersion.parse(version), channel, new ApiVersionRange(1, 1), null,
            Collections.emptyList(), "owner/example",
            Collections.singletonList(new ArtifactDescriptor("example-" + version + ".jar", PlatformType.BUKKIT, 8,
                null, null, null, null)),
            Collections.emptyList(), null, null, "");
    }
}
