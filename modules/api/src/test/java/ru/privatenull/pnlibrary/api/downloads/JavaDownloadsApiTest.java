package ru.privatenull.pnlibrary.api.downloads;

import org.junit.jupiter.api.Test;
import ru.privatenull.pnlibrary.api.platform.PlatformType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaDownloadsApiTest {
    @Test
    void declaresComponentsPluginsAndFilesFromJava() {
        PluginDownloads downloads = PluginDownloads.builder()
            .component("pneconomy", component -> component
                .version("2.0.0").apiVersions(1, 2).platform(PlatformType.BUKKIT)
                .java(17).url("https://example.org/pnEconomy.jar").automaticDownload(true))
            .plugin("Vault", plugin -> plugin
                .minimumVersion("1.7.3").url("https://example.org/Vault.jar"))
            .file("cases-data", file -> file
                .url("https://example.org/cases.bin")
                .destination(DownloadDestination.DATA_FOLDER, "resources/cases.bin"))
            .build();

        assertEquals(3, downloads.getDeclarations().size());
    }

    @Test
    void rejectsEscapingDestination() {
        assertThrows(IllegalArgumentException.class, () -> PluginDownloads.builder()
            .file("escape", file -> file.url("https://example.org/a.bin")
                .destination(DownloadDestination.DATA_FOLDER, "../outside.bin"))
            .build());
    }
}
