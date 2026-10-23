package org.pnfolder.pnlibrary.bukkit.entity

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ArmorStandVisualTest {
    private val world = mock(World::class.java)
    private val stand = mock(ArmorStand::class.java)

    private fun visual(): VisualEntity {
        `when`(world.spawnEntity(any(Location::class.java), eq(EntityType.ARMOR_STAND))).thenReturn(stand)
        return ArmorStandVisual.text(Location(world, 1.0, 10.0, 3.0), "Label")
    }

    @Test
    fun `rejected teleport preserves logical position`() {
        val visual = visual()
        `when`(stand.teleport(any(Location::class.java))).thenReturn(false)
        visual.teleport(Location(world, 4.0, 20.0, 6.0))
        assertEquals(10.0, visual.location.y)
    }

    @Test
    fun `successful teleport applies offset and copies logical location`() {
        val visual = visual()
        `when`(stand.teleport(any(Location::class.java))).thenReturn(true)
        val target = Location(world, 4.0, 20.0, 6.0)
        visual.teleport(target)
        val actual = ArgumentCaptor.forClass(Location::class.java)
        verify(stand).teleport(actual.capture())
        assertEquals(19.8, actual.value.y, 0.000001)
        target.y = 99.0
        val copy = visual.location
        copy.y = 88.0
        assertEquals(20.0, visual.location.y)
        assertNotSame(copy, visual.location)
    }

    @Test
    fun `rotation is reflected in logical location`() {
        val visual = visual()
        `when`(stand.teleport(any(Location::class.java))).thenReturn(true)
        visual.setRotation(90f, 15f)
        assertEquals(90f, visual.location.yaw)
        assertEquals(15f, visual.location.pitch)
    }

    @Test
    fun `removal also reaches an entity marked invalid`() {
        val visual = visual()
        `when`(stand.isValid).thenReturn(false)
        visual.remove()
        verify(stand).remove()
    }
}
