package org.pnfolder.pnlibrary.core.placeholders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.registry.Visibility;
import org.pnfolder.pnlibrary.core.registry.ListRegistry;

class PlaceholderRegistryTest {
    private final PlaceholderInfo coins =
            new PlaceholderInfo("shop.coins", "Coins", "shop/main", "Int", null, Collections.<String>emptyList(), Visibility.PLUGIN);
    private final Registry<PlaceholderInfo> registry = new ListRegistry<PlaceholderInfo>("placeholders", () -> Collections.singletonList(
            new RegistryEntry<PlaceholderInfo>(coins.getAddress(), coins, coins.getOwner(), coins.getVisibility(), coins.getDescription())));

    @Test
    void anEntryCarriesTheOwnerAndTheVisibilityOfThePlaceholder() {
        RegistryEntry<PlaceholderInfo> entry = registry.entries().get(0);

        assertEquals("shop.coins", entry.getKey());
        assertEquals("shop/main", entry.getOwner());
        assertEquals(Visibility.PLUGIN, entry.getVisibility());
        assertEquals(coins, registry.get("shop.coins"));
    }

    @Test
    void anUnknownAddressIsAbsentAndRequiredThrows() {
        assertNull(registry.get("shop.gems"));
        assertFalse(registry.contains("shop.gems"));
        assertThrows(NoSuchElementException.class, () -> registry.require("shop.gems"));
    }
}
