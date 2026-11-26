package org.pnfolder.pnlibrary.updates.engine.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;

class ReleaseCatalogCodecTest {
    private final ReleaseCatalogCodec codec = new ReleaseCatalogCodec();

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("decodes complete bukkit release without hashes")
    void decodesCompleteBukkitReleaseWithoutHashes() {
        ReleaseCatalog catalog = codec.decode(bytes("{\"schema\":1,\"product\":\"pnlibrary\",\"releases\":[{\n"
            + "  \"version\":\"2.6.0-beta.3\",\"channel\":\"beta\",\"description\":\"Test\",\n"
            + "  \"changelogUrl\":\"https://github.com/example/demo/blob/main/.pnlibrary/changelogs/2.6.0-beta.3.md\",\n"
            + "  \"publishedAt\":\"2026-09-29T12:00:00Z\",\n"
            + "  \"api\":{\"minimum\":1,\"maximum\":1},\"artifacts\":[{\n"
            + "    \"file\":\"pnLibrary-2.6.0-beta.3-bukkit-java8.jar\",\"platform\":\"BUKKIT\",\n"
            + "    \"minecraft\":{\"minimum\":\"1.20\",\"maximum\":\"1.21.11\"},\n"
            + "    \"java\":{\"minimum\":8,\"maximum\":21},\"url\":\"https://example.org/a.jar\"\n"
            + "  }]}]}"));

        CatalogRelease release = catalog.getReleases().get(0);
        assertEquals(1, catalog.getReleases().size());
        assertEquals("pnlibrary", catalog.getProduct());
        assertEquals("2.6.0-beta.3", release.getVersion().toString());
        assertEquals(UpdateChannel.BETA, release.getChannel());
        assertEquals("https://github.com/example/demo/blob/main/.pnlibrary/changelogs/2.6.0-beta.3.md",
            release.getChangelogUrl().toString());
        assertEquals(PlatformType.BUKKIT, release.getArtifacts().get(0).getPlatform());
        assertEquals("1.20", release.getArtifacts().get(0).getMinecraft().getMinimum());
    }

    @Test
    @DisplayName("rejects explicit null optional field")
    void rejectsExplicitNullOptionalField() {
        assertThrows(ManifestException.class, () -> codec.decode(bytes("{\"schema\":1,\"product\":\"demo\",\"releases\":[{\"version\":\"1.0.0\",\n"
            + "\"channel\":\"stable\",\"publishedAt\":\"2026-09-29T12:00:00Z\",\n"
            + "\"api\":{\"minimum\":1,\"maximum\":1},\"artifacts\":[{\n"
            + "\"file\":\"demo.jar\",\"platform\":\"VELOCITY\",\"platformApi\":null,\n"
            + "\"java\":{\"minimum\":17},\"url\":\"https://example.org/demo.jar\"}]}]}")));
    }

    @Test
    @DisplayName("uses explicitly declared channel instead of version suffix")
    void usesExplicitlyDeclaredChannelInsteadOfVersionSuffix() {
        CatalogRelease release = codec.decode(bytes("{\"schema\":1,\"product\":\"demo\",\"releases\":[{\"version\":\"2.2.0-rc.1\",\n"
            + "\"channel\":\"stable\",\"description\":\"Candidate release\",\"publishedAt\":\"2026-09-29T12:00:00Z\",\n"
            + "\"api\":{\"minimum\":1,\"maximum\":1},\"artifacts\":[{\n"
            + "\"file\":\"demo.jar\",\"platform\":\"BUKKIT\",\"java\":{\"minimum\":8},\n"
            + "\"url\":\"https://example.org/demo.jar\"}]}]}")).getReleases().get(0);

        assertEquals(UpdateChannel.STABLE, release.getChannel());
    }

    @Test
    @DisplayName("rejects non HTTPS changelog links")
    void rejectsNonHttpsChangelogLinks() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(bytes("{\"schema\":1,\"product\":\"demo\",\"releases\":[{\"version\":\"1.0.0\",\n"
            + "\"channel\":\"stable\",\"description\":\"Release\",\n"
            + "\"changelogUrl\":\"http://example.org/changes.md\",\n"
            + "\"publishedAt\":\"2026-09-29T12:00:00Z\",\"api\":{\"minimum\":1,\"maximum\":1},\n"
            + "\"artifacts\":[{\"file\":\"demo.jar\",\"platform\":\"BUKKIT\",\"java\":{\"minimum\":8},\n"
            + "\"url\":\"https://example.org/demo.jar\"}]}]}")));
    }
}
