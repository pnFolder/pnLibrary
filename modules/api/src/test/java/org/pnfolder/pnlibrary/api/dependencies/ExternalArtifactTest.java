package org.pnfolder.pnlibrary.api.dependencies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalArtifact;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

class ExternalArtifactTest {
    private static ExternalArtifact file(Set<PlatformType> platforms, String from, String before) {
        return new ExternalArtifact(URI.create("https://example.org/x.jar"), null, Collections.<URI>emptyList(),
            platforms, from, before, null, null);
    }

    private static ExternalArtifact file(String from, String before) {
        return file(Collections.<PlatformType>emptySet(), from, before);
    }

    private static String name(ExternalArtifact artifact) {
        String path = artifact.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Test
    void aFileWithoutLimitsFitsEveryServer() {
        assertTrue(file(null, null).fits(PlatformType.VELOCITY, null));
        assertTrue(file(null, null).fits(PlatformType.BUKKIT, "1.21.4"));
    }

    @Test
    void theMinecraftRangeIncludesItsStartAndLeavesOutItsEnd() {
        ExternalArtifact range = file("1.20", "1.21");

        assertTrue(range.fits(PlatformType.BUKKIT, "1.20"));
        assertTrue(range.fits(PlatformType.BUKKIT, "1.20.6"));
        assertFalse(range.fits(PlatformType.BUKKIT, "1.21"));
        assertFalse(range.fits(PlatformType.BUKKIT, "1.19.4"));
    }

    @Test
    void anUnknownMinecraftVersionFitsNoRangedFile() {
        assertFalse(file("1.20", null).fits(PlatformType.BUKKIT, null));
        assertFalse(file("1.20", null).fits(PlatformType.VELOCITY, "not a version"));
    }

    @Test
    void aPlatformLimitIsRespected() {
        ExternalArtifact proxy = file(EnumSet.of(PlatformType.VELOCITY), null, null);

        assertTrue(proxy.fits(PlatformType.VELOCITY, null));
        assertFalse(proxy.fits(PlatformType.BUKKIT, "1.21"));
    }

    @Test
    void badInputIsRefused() {
        URI https = URI.create("https://example.org/x.jar");
        List<URI> none = Collections.<URI>emptyList();
        Set<PlatformType> any = Collections.<PlatformType>emptySet();
        assertThrows(IllegalArgumentException.class,
            () -> new ExternalArtifact(URI.create("http://example.org/x.jar"), null));
        assertThrows(IllegalArgumentException.class, () -> new ExternalArtifact(https, "short"));
        assertThrows(IllegalArgumentException.class,
            () -> new ExternalArtifact(https, null, none, any, "latest", null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new ExternalArtifact(https, null, none, any, null, null, null, 0L));
    }

    @Test
    void theFirstFittingVariantWinsAndTheGeneralFileIsTheFallback() {
        Set<PlatformType> any = Collections.<PlatformType>emptySet();
        List<String> none = Collections.<String>emptyList();
        ExternalPluginDependency dependency = ExternalPluginDependency.builder("Vault", "1.7.0")
            .variant("https://example.org/old.jar", null, any, null, "1.20", none, null, null)
            .variant("https://example.org/new.jar", null, any, "1.20", null, none, null, null)
            .url("https://example.org/general.jar")
            .build();

        assertEquals("old.jar", name(dependency.artifactFor(PlatformType.BUKKIT, "1.16.5")));
        assertEquals("new.jar", name(dependency.artifactFor(PlatformType.BUKKIT, "1.21.4")));
        assertEquals("general.jar", name(dependency.artifactFor(PlatformType.VELOCITY, null)));
        assertEquals("general.jar", name(dependency.getArtifact()));
        assertNull(file(null, null).getLabel());
    }
}
