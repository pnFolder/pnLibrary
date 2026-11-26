package org.pnfolder.pnlibrary.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link VisualEntity} backed by an invisible marker armor stand. The stand stands {@code offset} below the visible
 * location: an item is worn on the head, a label floats just above the stand.
 */
final class ArmorStandVisual implements VisualEntity {
    private final ArmorStand stand;
    private final double offset;
    private Location visualLocation;

    ArmorStandVisual(ArmorStand stand, double offset, Location initialLocation) {
        this.stand = stand;
        this.offset = offset;
        this.visualLocation = initialLocation.clone();
    }

    @NotNull
    @Override
    public Entity getEntity() {
        return stand;
    }

    @Override
    public boolean isValid() {
        return stand.isValid() && !stand.isDead();
    }

    @NotNull
    @Override
    public Location getLocation() {
        return visualLocation.clone();
    }

    @Override
    public void teleport(@NotNull Location location) {
        Location target = ArmorStandVisuals.requireWorld(location).clone();
        if (stand.teleport(target.clone().subtract(0.0, offset, 0.0))) {
            visualLocation = target;
        }
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        Location target = getLocation();
        target.setYaw(yaw);
        target.setPitch(pitch);
        teleport(target);
    }

    @Override
    public void setScale(float scale) {
        stand.setSmall(scale > 0f && scale < 0.72f);
    }

    @Override
    public void setItem(@Nullable ItemStack item) {
        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(ArmorStandVisuals.safeItem(item, Material.CHEST));
        }
    }

    @Override
    public void setBlock(@Nullable Material material) {
        setItem(new ItemStack(ArmorStandVisuals.blockMaterial(material)));
    }

    @Override
    public void setText(@Nullable String text) {
        stand.setCustomName(text == null ? "" : text);
        stand.setCustomNameVisible(true);
    }

    @Override
    public void addScoreboardTag(@NotNull String tag) {
        if (!tag.trim().isEmpty()) {
            ArmorStandVisuals.optionalCapability(stand, "addScoreboardTag", String.class, tag);
        }
    }

    @Override
    public void remove() {
        stand.remove();
    }
}
