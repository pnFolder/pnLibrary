package ru.privatenull.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GlobalPlaceholderValueStoreTest {
    @Test
    fun `stores player values independently`() {
        val store = GlobalPlaceholderValueStore()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        assertTrue(store.create("player_level"))
        assertEquals("25", store.set("player_level", "25", first))
        assertEquals("25", store.get("player_level", first))
        assertNull(store.get("player_level", second))
    }

    @Test
    fun `command resolver preserves long underscore parameter names`() {
        val store = GlobalPlaceholderValueStore()
        val commands = DefaultValueCommandResolver(store)
        val player = UUID.randomUUID()

        assertEquals("true", commands.resolve("create_[player_total_time_played]", player))
        assertEquals("3600", commands.resolve("set_[player_total_time_played]_[3600]", player))
        assertEquals("3600", commands.resolve("get_[player_total_time_played]", player))
        assertEquals("3660", commands.resolve("increment_[player_total_time_played]_[60]", player))
        assertEquals("true", commands.resolve("exists_[player_total_time_played]", player))
        assertEquals("true", commands.resolve("remove_[player_total_time_played]", player))
        assertNull(commands.resolve("get_[player_total_time_played]", player))
    }

    @Test
    fun `global values do not require a player`() {
        val store = GlobalPlaceholderValueStore()
        val commands = DefaultValueCommandResolver(store)

        assertEquals("true", commands.resolve("createglobal_[server_restart_count]", null))
        assertEquals("1", commands.resolve("set_[server_restart_count]_[1]", null))
        assertEquals("1", commands.resolve("get_[server_restart_count]", null))
        assertFalse(store.create("server_restart_count"))
    }

    @Test
    fun `brackets preserve underscores in values and reject ambiguous syntax`() {
        val store = GlobalPlaceholderValueStore()
        val commands = DefaultValueCommandResolver(store)

        assertEquals("true", commands.resolve("createglobal_[selected_rank]", null))
        assertEquals("VIP_player_01", commands.resolve("set_[selected_rank]_[VIP_player_01]", null))
        assertEquals("VIP_player_01", commands.resolve("get_[selected_rank]", null))
        assertNull(commands.resolve("set_selected_rank_VIP_player_01", null))
    }
}
