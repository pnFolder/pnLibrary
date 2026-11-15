package org.pnfolder.pnlibrary.bukkit.paper

import com.mojang.brigadier.StringReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpacelessWordTest {
    @Test
    fun `an id and a value with slashes colons and semicolons are read whole`() {
        val reader = StringReader("shop/main:config.yml respawn world;0;64;0")

        assertEquals("shop/main:config.yml", SpacelessWord.parse(reader))
        reader.skip()
        assertEquals("respawn", SpacelessWord.parse(reader))
    }
}
