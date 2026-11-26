package org.pnfolder.pnlibrary.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Spawns {@link ArmorStandVisual} instances: the visual service of the Bukkit runtime. */
public final class ArmorStandVisuals implements VisualService {
    public static final ArmorStandVisuals INSTANCE = new ArmorStandVisuals();

    private static final double HEAD_OFFSET = 1.55;
    private static final double TEXT_OFFSET = 0.20;

    private ArmorStandVisuals() {
    }

    @NotNull
    @Override
    public VisualEntity item(@NotNull Location location, @Nullable ItemStack item) {
        Location target = requireWorld(location).clone();
        ArmorStand stand = spawn(target.clone().subtract(0.0, HEAD_OFFSET, 0.0));
        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(safeItem(item, Material.NETHER_STAR));
        }
        return new ArmorStandVisual(stand, HEAD_OFFSET, target);
    }

    @NotNull
    @Override
    public VisualEntity block(@NotNull Location location, @Nullable Material material) {
        return item(location, new ItemStack(blockMaterial(material)));
    }

    @NotNull
    @Override
    public VisualEntity text(@NotNull Location location, @Nullable String text) {
        Location target = requireWorld(location).clone();
        ArmorStand stand = spawn(target.clone().subtract(0.0, TEXT_OFFSET, 0.0));
        ArmorStandVisual visual = new ArmorStandVisual(stand, TEXT_OFFSET, target);
        visual.setText(text);
        return visual;
    }

    private static ArmorStand spawn(Location location) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setCustomName("");
        stand.setCustomNameVisible(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        optionalCapability(stand, "setCollidable", Boolean.TYPE, false);
        optionalCapability(stand, "setSilent", Boolean.TYPE, true);
        optionalCapability(stand, "setInvulnerable", Boolean.TYPE, true);
        optionalCapability(stand, "setPersistent", Boolean.TYPE, false);
        return stand;
    }

    static Location requireWorld(Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Visual location must have a world");
        }
        return location;
    }

    static ItemStack safeItem(ItemStack item, Material fallback) {
        if (item == null || isAir(item.getType())) {
            return new ItemStack(fallback);
        }
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    static Material blockMaterial(Material material) {
        return material == null || isAir(material) ? Material.CHEST : material;
    }

    private static boolean isAir(Material material) {
        return material.name().equals("AIR") || material.name().endsWith("_AIR");
    }

    static void optionalCapability(Object receiver, String name, Class<?> type, Object argument) {
        try {
            receiver.getClass().getMethod(name, type).invoke(receiver, argument);
        } catch (ReflectiveOperationException error) {
            // This capability is absent on older servers.
        } catch (RuntimeException error) {
            // A server implementation may reject an optional property.
        }
    }
}
