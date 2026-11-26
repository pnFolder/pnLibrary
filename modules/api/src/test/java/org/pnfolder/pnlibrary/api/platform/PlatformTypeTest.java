package org.pnfolder.pnlibrary.api.platform;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformTypeTest {
    @Test
    void onlyApiFamiliesPlusTheUniversalReleaseArtifactArePlatformTypes() {
        assertEquals(
                Arrays.asList(PlatformType.BUKKIT, PlatformType.BUNGEECORD, PlatformType.VELOCITY, PlatformType.UNIVERSAL),
                Arrays.asList(PlatformType.values()));
    }

    @Test
    void universalIsNotAProxyRoleThoughItIsNeverARealPluginIdentityInTheFirstPlace() {
        assertFalse(PlatformType.UNIVERSAL.isProxy());
    }

    @Test
    void platformTypesDescribeServerAndProxyRoles() {
        assertTrue(PlatformType.BUKKIT.isServer());
        assertFalse(PlatformType.BUKKIT.isProxy());

        for (PlatformType type : Arrays.asList(PlatformType.BUNGEECORD, PlatformType.VELOCITY)) {
            assertTrue(type.isProxy());
            assertFalse(type.isServer());
        }
    }

    @Test
    void typeIdentifiersAreStableAndIndependentOfImplementations() {
        assertEquals("bukkit", PlatformType.BUKKIT.getId());
        assertEquals("bungeecord", PlatformType.BUNGEECORD.getId());
        assertEquals("velocity", PlatformType.VELOCITY.getId());
    }
}
