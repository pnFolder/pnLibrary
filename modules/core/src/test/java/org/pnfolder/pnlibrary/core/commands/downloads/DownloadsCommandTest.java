package org.pnfolder.pnlibrary.core.commands.downloads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;

class DownloadsCommandTest {
    @Test
    @DisplayName("downloads of every module are listed with who asked and the state")
    void downloadsOfEveryModuleAreListedWithWhoAskedAndTheState() {
        DownloadRegistration geoip = mock(DownloadRegistration.class);
        when(geoip.snapshots()).thenReturn(Arrays.asList(new DownloadSnapshot("geoip", DownloadState.STAGED),
                new DownloadSnapshot("skins", DownloadState.FAILED, "нет сети")));
        ModuleContext module = mock(ModuleContext.class);
        when(module.getId()).thenReturn(ModuleId.of("main"));
        when(module.getDownloads()).thenReturn(geoip);
        ModuleContext plain = mock(ModuleContext.class);
        when(plain.getId()).thenReturn(ModuleId.of("plain"));
        when(plain.getDownloads()).thenReturn(null);
        PluginRegistration registration = mock(PluginRegistration.class);
        when(registration.getId()).thenReturn(PluginId.of("shop"));
        when(registration.all()).thenReturn(Arrays.asList(module, plain));
        PluginRegistry registry = mock(PluginRegistry.class);
        when(registry.all()).thenReturn(Arrays.asList(registration));

        List<DeclaredDownload> all = DownloadsCommand.declaredDownloads(registry);

        List<String> shown = new ArrayList<String>();
        for (DeclaredDownload download : all) {
            shown.add(download.getOwner() + " " + download.getSnapshot().getKey());
        }
        assertEquals(Arrays.asList("shop/main geoip", "shop/main skins"), shown);
        assertEquals("нет сети", all.get(all.size() - 1).getSnapshot().getMessage());
    }
}
