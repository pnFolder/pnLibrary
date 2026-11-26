package org.pnfolder.pnlibrary.bukkit.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.brigadier.StringReader;
import org.junit.jupiter.api.Test;

class SpacelessWordTest {
    @Test
    void anIdAndAValueWithSlashesColonsAndSemicolonsAreReadWhole() {
        StringReader reader = new StringReader("shop/main:config.yml respawn world;0;64;0");

        assertEquals("shop/main:config.yml", SpacelessWord.INSTANCE.parse(reader));
        reader.skip();
        assertEquals("respawn", SpacelessWord.INSTANCE.parse(reader));
    }
}
