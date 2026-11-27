package org.pnfolder.pnlibrary.bukkit.audience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogCommands.SettingSaved;

class BukkitTitleTest {
    @Test
    void aChangedSettingShowsTheOldValueInLilacAndTheNewOneInWhite() {
        BukkitTitle title = BukkitTitle.settingSaved(new SettingSaved("pnLibrary", "Канал", "stable", "beta"));

        assertEquals(new BukkitTitle("§d✔ §fКанал", "§dstable§d → §fbeta"), title);
    }

    @Test
    void anUnchangedSettingShowsOnlyItsValue() {
        assertEquals("§dвключена", BukkitTitle.settingSaved(
            new SettingSaved("pnLibrary", "Автозагрузка", "включена", "включена")).getSubtitle());
    }

    @Test
    void pendingUpdatesDownsampleToLegacyColorsFor18Clients() {
        assertEquals(new BukkitTitle("§dОбновления pnFolder", "§fДоступно: §d3"), BukkitTitle.updatesPending(3));
    }
}
