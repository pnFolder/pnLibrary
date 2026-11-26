package org.pnfolder.pnlibrary.api.downloads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;

class FileDownloadsTest {
    @Test
    @DisplayName("a file is manual and required until said otherwise")
    void aFileIsManualAndRequiredUntilSaidOtherwise() {
        FileDownloads files = FileDownloads.builder()
            .file("geoip", it -> it.url("https://example.com/a.bin").destination(DownloadDestination.CACHE, "a.bin"))
            .build();
        FileDownload file = files.getFiles().get(0);

        assertEquals(DownloadPolicy.MANUAL, file.getPolicy());
        assertTrue(file.getRequired());
    }

    @Test
    @DisplayName("policy and optional replace the flags")
    void policyAndOptionalReplaceTheFlags() {
        FileDownloads files = FileDownloads.builder().file("geoip", it -> it.url("https://example.com/a.bin")
            .destination(DownloadDestination.DATA_FOLDER, "a.bin").policy(DownloadPolicy.FORCED).optional()).build();
        FileDownload file = files.getFiles().get(0);

        assertEquals(DownloadPolicy.FORCED, file.getPolicy());
        assertFalse(file.getRequired());
    }

    @Test
    @DisplayName("a file can be declared with its address and path alone")
    void aFileCanBeDeclaredWithItsAddressAndPathAlone() {
        FileDownload file = FileDownloads.builder()
            .file("geoip", "https://example.com/geoip.mmdb", "geoip/geoip.mmdb").build().getFiles().get(0);

        assertEquals("geoip", file.getKey());
        assertEquals(DownloadDestination.DATA_FOLDER, file.getDestination());
        assertEquals("geoip/geoip.mmdb", file.getRelativePath());
    }

    @Test
    @DisplayName("an address that is not https and a path outside the folder are refused")
    void anAddressThatIsNotHttpsAndAPathOutsideTheFolderAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> FileDownloads.builder().file("a", "http://example.com/a", "a.bin"));
        assertThrows(IllegalArgumentException.class,
            () -> FileDownloads.builder().file("a", "https://example.com/a", "../a.bin"));
    }
}
