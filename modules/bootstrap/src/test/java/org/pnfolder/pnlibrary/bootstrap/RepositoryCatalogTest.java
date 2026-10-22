package org.pnfolder.pnlibrary.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RepositoryCatalogTest {
    private static final String SUM = "c123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static String list(String... releases) {
        return "{\"schema\":1,\"product\":\"pnlibrary\",\"releases\":[" + String.join(",", releases) + "]}";
    }

    private static String release(String version, String channel) {
        return "{\"version\":\"" + version + "\",\"channel\":\"" + channel + "\"}";
    }

    private static String releaseWithArtifacts(String version, String channel, String... artifacts) {
        return "{\"version\":\"" + version + "\",\"channel\":\"" + channel + "\",\"artifacts\":["
                + String.join(",", artifacts) + "]}";
    }

    private static String artifact(String platform, String file, String sum) {
        return artifact(platform, file, sum, null, null);
    }

    private static String artifact(String platform, String file, String sum, Long size, Integer javaMinimum) {
        StringBuilder json = new StringBuilder("{\"platform\":\"").append(platform).append("\",\"file\":\"")
                .append(file).append("\",\"url\":\"https://example.org/").append(file)
                .append("\",\"sha256\":\"").append(sum).append('"');
        if (size != null) json.append(",\"size\":").append(size);
        if (javaMinimum != null) json.append(",\"java\":{\"minimum\":").append(javaMinimum).append('}');
        return json.append('}').toString();
    }

    @Test
    void anEmptyListSuggestsNothing() {
        assertNull(RepositoryCatalog.newestStable(list(), "1.0.0"));
    }

    @Test
    void theNewestStableReleaseWinsWhateverTheOrder() {
        String json = list(release("1.0.0", "stable"), release("1.2.0", "stable"), release("1.1.0", "stable"));

        assertEquals("1.2.0", RepositoryCatalog.newestStable(json, "1.0.0"));
    }

    @Test
    void preReleasesAndOlderVersionsAreSkipped() {
        String json = list(release("2.0.0-beta.1", "beta"), release("1.5.0", "stable"), release("0.9.0", "stable"));

        assertEquals("1.5.0", RepositoryCatalog.newestStable(json, "1.0.0"));
        assertNull(RepositoryCatalog.newestStable(json, "1.6.0"));
    }

    @Test
    void whenNothingIsStableTheNewestOfAnyKindIsOffered() {
        String json = list(release("2.0.0-beta.1", "beta"), release("2.0.0-beta.2", "beta"), release("1.0.0-dev.1", "dev"));

        assertNull(RepositoryCatalog.newestStable(json, "1.0.0"));
        assertEquals("2.0.0-beta.2", RepositoryCatalog.newestAny(json, "0.0.0"));
        assertNull(RepositoryCatalog.newestAny(list(), "0.0.0"));
    }

    @Test
    void anExactPlatformArtifactWinsOverAUniversalOneInTheSameRelease() {
        String json = list(releaseWithArtifacts("1.0.0", "stable",
                artifact("UNIVERSAL", "pnLibrary-1.0.0-universal.jar", SUM),
                artifact("BUKKIT", "pnLibrary-1.0.0-bukkit-java8.jar", SUM)));

        RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L, BootstrapPlatform.BUKKIT, 8);

        assertEquals("1.0.0", listed.version());
        assertEquals("pnLibrary-1.0.0-bukkit-java8.jar", listed.release().fileName());
    }

    @Test
    void aUniversalArtifactIsUsedWhenNoExactPlatformOneIsListed() {
        String json = list(releaseWithArtifacts("1.0.0", "stable",
                artifact("UNIVERSAL", "pnLibrary-1.0.0-universal.jar", SUM)));

        RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L, BootstrapPlatform.VELOCITY, 21);

        assertEquals("pnLibrary-1.0.0-universal.jar", listed.release().fileName());
        assertEquals(SUM, listed.release().sha256());
    }

    @Test
    void anArtifactOfAnotherPlatformIsNeverUsed() {
        String json = list(releaseWithArtifacts("1.0.0", "stable",
                artifact("BUNGEECORD", "pnLibrary-1.0.0-bungeecord-java8.jar", SUM)));

        RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L, BootstrapPlatform.BUKKIT, 8);

        assertEquals("1.0.0", listed.version());
        assertNull(listed.release());
    }

    @Test
    void anArtifactWhoseJavaRangeDoesNotFitIsSkipped() {
        String json = list(releaseWithArtifacts("1.0.0", "stable",
                artifact("BUKKIT", "pnLibrary-1.0.0-bukkit-java17.jar", SUM, null, 17)));

        RepositoryCatalog.Listed onOldJava = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L, BootstrapPlatform.BUKKIT, 8);
        RepositoryCatalog.Listed onNewJava = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L, BootstrapPlatform.BUKKIT, 21);

        assertNull(onOldJava.release());
        assertEquals("pnLibrary-1.0.0-bukkit-java17.jar", onNewJava.release().fileName());
    }

    @Test
    void whenNoPlatformIsGivenTheVersionIsStillPicked() {
        String json = list(releaseWithArtifacts("1.0.0", "stable", artifact("UNIVERSAL", "pnLibrary-1.0.0-universal.jar", SUM)));

        RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L, null, 0);

        assertEquals("1.0.0", listed.version());
        assertNull(listed.release());
    }

    @Test
    void anIncompleteOrUnsafeArtifactIsIgnoredButTheVersionStays() {
        String[] entries = {
                artifact("UNIVERSAL", "other.jar", SUM),
                "{\"platform\":\"UNIVERSAL\",\"file\":\"pnLibrary-1.0.0-universal.jar\",\"url\":\"http://example.org/a.jar\",\"sha256\":\"" + SUM + "\"}",
                "{\"platform\":\"UNIVERSAL\",\"file\":\"pnLibrary-1.0.0-universal.jar\",\"url\":\"https://example.org/a.jar\",\"sha256\":\"short\"}",
                artifact("UNIVERSAL", "pnLibrary-1.0.0-universal.jar", SUM, 99_999_999L, null),
        };
        for (String entry : entries) {
            String json = list(releaseWithArtifacts("1.0.0", "stable", entry));

            RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L, BootstrapPlatform.BUKKIT, 8);

            assertEquals("1.0.0", listed.version(), entry);
            assertNull(listed.release(), entry);
        }
    }

    @Test
    void brokenEntriesAreIgnoredAndBrokenFilesSuggestNothing() {
        String json = list("\"text\"", release("not-a-version", "stable"), "{\"channel\":\"stable\"}", release("1.0.1", "stable"));

        assertEquals("1.0.1", RepositoryCatalog.newestStable(json, "1.0.0"));
        assertNull(RepositoryCatalog.newestStable("not json", "1.0.0"));
        assertNull(RepositoryCatalog.newestStable("[]", "1.0.0"));
        assertNull(RepositoryCatalog.newestStable("{\"releases\":5}", "1.0.0"));
    }
}
