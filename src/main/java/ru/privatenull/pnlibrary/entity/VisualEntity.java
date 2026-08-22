package ru.privatenull.pnlibrary.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/** Version-neutral armor-stand visual used by plugin animations. */
public final class VisualEntity {
    private static final double HEAD_OFFSET = 1.55;
    private static final double TEXT_OFFSET = 0.20;

    private final Entity entity;
    private final Kind kind;
    private Location visualLocation;

    private VisualEntity(Entity entity, Kind kind, Location visualLocation) {
        this.entity = entity;
        this.kind = kind;
        this.visualLocation = visualLocation == null ? null : visualLocation.clone();
    }

    public static VisualEntity item(Location location, ItemStack item) {
        ArmorStand stand = spawn(location.clone().subtract(0.0, HEAD_OFFSET, 0.0));
        helmet(stand, safeItem(item, Material.NETHER_STAR));
        return new VisualEntity(stand, Kind.ITEM, location);
    }

    public static VisualEntity block(Location location, Material material) {
        return item(location, new ItemStack(material == null || material.isAir() ? Material.CHEST : material));
    }

    public static VisualEntity text(Location location, String text) {
        ArmorStand stand = spawn(location.clone().subtract(0.0, TEXT_OFFSET, 0.0));
        stand.setCustomName(text == null ? "" : text);
        stand.setCustomNameVisible(true);
        return new VisualEntity(stand, Kind.TEXT, location);
    }

    public Entity entity() { return entity; }
    public boolean isValid() { return entity != null && entity.isValid() && !entity.isDead(); }
    public boolean isDead() { return entity == null || entity.isDead(); }
    public Location getLocation() {
        return visualLocation != null ? visualLocation.clone() : entity == null ? null : entity.getLocation();
    }

    public void teleport(Location location) {
        if (entity == null || location == null) return;
        visualLocation = location.clone();
        double offset = kind == Kind.ITEM ? HEAD_OFFSET : kind == Kind.TEXT ? TEXT_OFFSET : 0.0;
        entity.teleport(location.clone().subtract(0.0, offset, 0.0));
    }

    public void setRotation(float yaw, float pitch) {
        if (entity != null) entity.setRotation(yaw, pitch);
    }

    public void setScale(float scale) {
        if (entity instanceof ArmorStand stand) stand.setSmall(scale > 0.0f && scale < 0.72f);
    }

    public void setItem(ItemStack item) {
        if (entity instanceof ArmorStand stand) helmet(stand, safeItem(item, Material.CHEST));
    }

    public void setBlock(Material material) {
        setItem(new ItemStack(material == null || material.isAir() ? Material.CHEST : material));
    }

    public void setText(String text) {
        if (entity instanceof ArmorStand stand) {
            stand.setCustomName(text == null ? "" : text);
            stand.setCustomNameVisible(true);
        }
    }

    public void addScoreboardTag(String tag) {
        if (entity != null && tag != null && !tag.isBlank()) entity.addScoreboardTag(tag);
    }

    public void remove() {
        if (entity != null && entity.isValid()) entity.remove();
    }

    private static ArmorStand spawn(Location location) {
        World world = location == null ? null : location.getWorld();
        if (world == null) throw new IllegalArgumentException("Location must have world");
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setCustomName("");
        stand.setCustomNameVisible(false);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        return stand;
    }

    private static void helmet(ArmorStand stand, ItemStack item) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment != null) equipment.setHelmet(item);
    }

    private static ItemStack safeItem(ItemStack item, Material fallback) {
        if (item == null || item.getType().isAir()) return new ItemStack(fallback);
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

    private enum Kind { ITEM, TEXT }
}
