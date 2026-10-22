package org.pnfolder.pnlibrary.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RepositoryCatalogTest {
    private static String list(String... releases) {
        return "{\"schema\":1,\"product\":\"pnlibrary\",\"releases\":[" + String.join(",", releases) + "]}";
    }

    private static String release(String version, String channel) {
        return "{\"version\":\"" + version + "\",\"channel\":\"" + channel + "\"}";
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
    void theInstallerBlockOfTheNewestReleaseDescribesItsFile() {
        String sum = "c123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String json = list("{\"version\":\"1.0.0\",\"channel\":\"stable\",\"installer\":{"
                + "\"file\":\"pnLibrary-1.0.0-universal.jar\",\"url\":\"https://example.org/a.jar\","
                + "\"sha256\":\"" + sum + "\",\"size\":2048}}");

        RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L);

        assertEquals("1.0.0", listed.version());
        assertEquals("https://example.org/a.jar", listed.release().uri().toString());
        assertEquals(sum, listed.release().sha256());
        assertEquals(2048L, listed.release().size());
    }

    @Test
    void anIncompleteOrUnsafeInstallerBlockIsIgnoredButTheVersionStays() {
        String[] blocks = {
                "{\"file\":\"other.jar\",\"url\":\"https://example.org/a.jar\",\"sha256\":\"%s\"}",
                "{\"file\":\"pnLibrary-1.0.0-universal.jar\",\"url\":\"http://example.org/a.jar\",\"sha256\":\"%s\"}",
                "{\"file\":\"pnLibrary-1.0.0-universal.jar\",\"url\":\"https://example.org/a.jar\",\"sha256\":\"short\"}",
                "{\"file\":\"pnLibrary-1.0.0-universal.jar\",\"url\":\"https://example.org/a.jar\",\"sha256\":\"%s\",\"size\":99999999}",
        };
        String sum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        for (String block : blocks) {
            String json = list("{\"version\":\"1.0.0\",\"channel\":\"stable\",\"installer\":"
                    + String.format(block, sum) + "}");

            RepositoryCatalog.Listed listed = RepositoryCatalog.pick(json, "0.0.0", true, 1_000_000L);

            assertEquals("1.0.0", listed.version(), block);
            assertNull(listed.release(), block);
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
