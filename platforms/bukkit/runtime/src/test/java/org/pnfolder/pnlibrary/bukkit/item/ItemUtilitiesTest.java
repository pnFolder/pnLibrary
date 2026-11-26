package org.pnfolder.pnlibrary.bukkit.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ItemUtilitiesTest {
    @Test
    void itemCodecRejectsMalformedPayloads() {
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.decode("not-base64"));
    }

    @Test
    void itemCodecRejectsValidSerializationOfANonItem() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject("not an item");
        }
        String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.decode(encoded));
        assertEquals("Encoded payload does not contain an ItemStack", error.getCause().getMessage());
    }

    @Test
    void itemCodecRejectsMissingPayloads() {
        for (String value : Arrays.asList("", "  ")) {
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.decode(value));
        }
    }

    @Test
    void textureNormalizationAcceptsHashesUrlsAndBase64() {
        String hash = "0123456789abcdef0123456789abcdef";
        String url = "https://textures.minecraft.net/texture/" + hash;
        String encoded = Base64.getEncoder().encodeToString(
            ("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(StandardCharsets.UTF_8));

        assertEquals(hash, PlayerHeads.normalizeTexture(hash));
        assertEquals(url, PlayerHeads.normalizeTexture(url));
        assertEquals(encoded, PlayerHeads.normalizeTexture("base64:" + encoded));
        assertNull(PlayerHeads.normalizeTexture("not a texture"));
    }

    @Test
    void realItemCheckExcludesNullAirAndEmptyStacks() {
        assertFalse(BukkitItems.INSTANCE.isReal(null));
        assertFalse(BukkitItems.INSTANCE.isReal(new ItemStack(Material.AIR)));
        assertTrue(BukkitItems.INSTANCE.isReal(new ItemStack(Material.STONE)));
    }
}
