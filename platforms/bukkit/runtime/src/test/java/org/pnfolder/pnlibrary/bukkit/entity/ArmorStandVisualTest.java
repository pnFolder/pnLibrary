package org.pnfolder.pnlibrary.bukkit.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArmorStandVisualTest {
    private final World world = mock(World.class);
    private final ArmorStand stand = mock(ArmorStand.class);

    private VisualEntity visual() {
        when(world.spawnEntity(any(Location.class), eq(EntityType.ARMOR_STAND))).thenReturn(stand);
        return ArmorStandVisuals.INSTANCE.text(new Location(world, 1.0, 10.0, 3.0), "Label");
    }

    @Test
    void rejectedTeleportPreservesLogicalPosition() {
        VisualEntity visual = visual();
        when(stand.teleport(any(Location.class))).thenReturn(false);
        visual.teleport(new Location(world, 4.0, 20.0, 6.0));
        assertEquals(10.0, visual.getLocation().getY());
    }

    @Test
    void successfulTeleportAppliesOffsetAndCopiesLogicalLocation() {
        VisualEntity visual = visual();
        when(stand.teleport(any(Location.class))).thenReturn(true);
        Location target = new Location(world, 4.0, 20.0, 6.0);
        visual.teleport(target);
        ArgumentCaptor<Location> actual = ArgumentCaptor.forClass(Location.class);
        verify(stand).teleport(actual.capture());
        assertEquals(19.8, actual.getValue().getY(), 0.000001);
        target.setY(99.0);
        Location copy = visual.getLocation();
        copy.setY(88.0);
        assertEquals(20.0, visual.getLocation().getY());
        assertNotSame(copy, visual.getLocation());
    }

    @Test
    void rotationIsReflectedInLogicalLocation() {
        VisualEntity visual = visual();
        when(stand.teleport(any(Location.class))).thenReturn(true);
        visual.setRotation(90f, 15f);
        assertEquals(90f, visual.getLocation().getYaw());
        assertEquals(15f, visual.getLocation().getPitch());
    }

    @Test
    void removalAlsoReachesAnEntityMarkedInvalid() {
        VisualEntity visual = visual();
        when(stand.isValid()).thenReturn(false);
        visual.remove();
        verify(stand).remove();
    }
}
