package org.pnfolder.pnlibrary.bukkit.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionRange;

class ServerInfoTest {
    private final ServerInfo server = new ServerInfo("Paper", "git-Paper-120", MinecraftVersion.V1_21_4, "1.21.4");

    @Test
    void serverExposesSoftwareAndMinecraftVersionsSeparately() {
        assertEquals("Paper git-Paper-120", server.getDisplayName());
        assertEquals("git-Paper-120", server.getVersion());
        assertEquals("1.21.4", server.getRawMinecraftVersion());
    }

    @Test
    void serverProvidesReadableCompatibilityChecks() {
        assertTrue(server.isMinecraftAtLeast(MinecraftVersion.V1_20_5));
        assertTrue(server.supports(MinecraftVersionRange.atLeast(MinecraftVersion.V1_21)));
        assertFalse(server.supports(MinecraftVersionRange.atMost(MinecraftVersion.V1_20_6)));
    }
}
