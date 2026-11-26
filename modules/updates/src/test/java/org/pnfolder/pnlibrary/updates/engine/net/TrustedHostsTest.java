package org.pnfolder.pnlibrary.updates.engine.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrustedHostsTest {
    @Test
    @DisplayName("github release and cdn links pass")
    void githubReleaseAndCdnLinksPass() {
        String[] links = {
            "https://github.com/PnFolder/pnLibrary/releases/download/v1/pnLibrary-1.0.0-universal.jar",
            "https://objects.githubusercontent.com/x/y.jar",
            "https://release-assets.githubusercontent.com/x/y.jar",
            "https://raw.githubusercontent.com/PnFolder/pnLibrary/main/x.jar",
            "https://cdn.jsdelivr.net/gh/PnFolder/pnLibrary@main/x.jar",
        };
        for (String link : links) {
            assertEquals(URI.create(link), TrustedHosts.requireDownload(URI.create(link)));
        }
    }

    @Test
    @DisplayName("foreign host and plain http are refused")
    void foreignHostAndPlainHttpAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> TrustedHosts.requireDownload(URI.create("https://evil.example.com/a.jar")));
        assertThrows(IllegalArgumentException.class, () -> TrustedHosts.requireDownload(URI.create("https://github.com.evil.com/a.jar")));
        assertThrows(IllegalArgumentException.class, () -> TrustedHosts.requireDownload(URI.create("http://github.com/a.jar")));
        assertThrows(IllegalArgumentException.class, () -> TrustedHosts.requireRedirect(URI.create("https://evil.example.com/a.jar")));
    }
}
