package org.pnfolder.pnlibrary.api.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;

class PluginUpdateRequestTest {
    @Test
    void updateDeclarationDescribesRepositoryApiAndPlatformArtifacts() {
        PluginUpdateRequest request = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnCases")
            .supportedApi(1, 2)
            .artifact("(?i)^pnCases-Bukkit-.*\\.jar$", PlatformType.BUKKIT, 17)
            .build();

        assertEquals(new ApiVersionRange(1, 2), request.getSupportedApi());
        assertEquals(1, request.getArtifacts().size());
        assertEquals(PlatformType.BUKKIT, request.getArtifacts().get(0).getPlatform());
    }

    @Test
    void aPluginWithOneJarNeedsNoArtifactRule() {
        PluginUpdateRequest request = PluginUpdateRequest.builder().repository("pnFolder", "pnCases").build();

        assertTrue(request.getArtifacts().isEmpty());
    }

    @Test
    void channelDefaultsToStableAndAutomaticDownloadIsOnUntilAnAdministratorChangesThem() {
        PluginUpdateRequest request = PluginUpdateRequest.builder()
            .repository("pnFolder", "pnCases")
            .exactArtifact("pnCases.jar")
            .build();

        assertEquals(UpdateChannel.STABLE, request.getChannel());
    }

    @Test
    void legacyDeclarationsReceiveResolverCompatibleApiDefaults() {
        PluginUpdateRequest request = PluginUpdateRequest.builder()
            .repository("pnFolder", "PnMarket")
            .artifact("(?i)^pnmarket-.*\\.jar$", 8)
            .build();

        assertTrue(request.getSupportedApi().supports(PnLibraryApi.VERSION));
    }

    @Test
    void declaresExactArtifactAndApiRange() {
        PluginUpdateRequest request = PluginUpdateRequest.builder()
            .repository("pnFolder", "PnMarket")
            .supportedApi(3, 5)
            .exactArtifact("pnMarket-4.0.0+api5.jar", 17)
            .build();

        Pattern pattern = Pattern.compile(request.getArtifacts().get(0).getPattern());
        assertEquals(new ApiVersionRange(3, 5), request.getSupportedApi());
        assertTrue(pattern.matcher("pnMarket-4.0.0+api5.jar").matches());
        assertFalse(pattern.matcher("prefix-pnMarket-4.0.0+api5.jar").matches());
    }
}
