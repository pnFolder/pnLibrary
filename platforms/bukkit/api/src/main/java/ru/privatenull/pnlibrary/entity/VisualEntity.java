package ru.privatenull.pnlibrary.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** Version-neutral armor-stand visual for lightweight Bukkit animations. */
public final class VisualEntity {
    private static final double HEAD_OFFSET = 1.55;
    private static final double TEXT_OFFSET = 0.20;

    private final Entity entity;
    private final Kind kind;
    private Location visualLocation;

    private VisualEntity(Entity entity, Kind kind, Location visualLocation) {
        this.entity = entity;
        this.kind = kind;
        this.visualLocation = visualLocation.clone();
    }

    /** Spawns an item visual whose logical location is the center of the displayed item. */
    public static VisualEntity item(Location location, ItemStack item) {
        requireLocation(location);
        ArmorStand stand = spawn(location.clone().subtract(0.0, HEAD_OFFSET, 0.0));
        helmet(stand, safeItem(item, Material.NETHER_STAR));
        return new VisualEntity(stand, Kind.ITEM, location);
    }

    /** Spawns a block visual backed by an armor-stand helmet. */
    public static VisualEntity block(Location location, Material material) {
        Material selected = material == null || isAir(material) ? Material.CHEST : material;
        return item(location, new ItemStack(selected));
    }

    /** Spawns a floating legacy-text label. */
    public static VisualEntity text(Location location, String text) {
        requireLocation(location);
        ArmorStand stand = spawn(location.clone().subtract(0.0, TEXT_OFFSET, 0.0));
        stand.setCustomName(text == null ? "" : text);
        stand.setCustomNameVisible(true);
        return new VisualEntity(stand, Kind.TEXT, location);
    }

    /** Returns the underlying Bukkit entity for lifecycle tracking. */
    public Entity entity() {
        return entity;
    }

    /** Returns whether the underlying entity is alive and valid. */
    public boolean isValid() {
        return entity.isValid() && !entity.isDead();
    }

    /** Returns whether the underlying entity has died or been removed. */
    public boolean isDead() {
        return entity.isDead() || !entity.isValid();
    }

    /** Returns the logical visual location rather than the offset armor-stand location. */
    public Location getLocation() {
        return visualLocation.clone();
    }

    /** Moves the visual while retaining the correct display offset. */
    public void teleport(Location location) {
        requireLocation(location);
        visualLocation = location.clone();
        entity.teleport(location.clone().subtract(0.0, offset(), 0.0));
    }

    /** Changes the entity yaw and pitch. */
    public void setRotation(float yaw, float pitch) {
        Location current = entity.getLocation();
        current.setYaw(yaw);
        current.setPitch(pitch);
        entity.teleport(current);
    }

    /** Uses the armor stand's small model for scales below {@code 0.72}. */
    public void setScale(float scale) {
        if (entity instanceof ArmorStand) ((ArmorStand) entity).setSmall(scale > 0.0f && scale < 0.72f);
    }

    /** Replaces the displayed item. */
    public void setItem(ItemStack item) {
        if (entity instanceof ArmorStand) helmet((ArmorStand) entity, safeItem(item, Material.CHEST));
    }

    /** Replaces the displayed block material. */
    public void setBlock(Material material) {
        Material selected = material == null || isAir(material) ? Material.CHEST : material;
        setItem(new ItemStack(selected));
    }

    /** Replaces the floating label text. */
    public void setText(String text) {
        if (entity instanceof ArmorStand) {
            entity.setCustomName(text == null ? "" : text);
            entity.setCustomNameVisible(true);
        }
    }

    /** Adds a scoreboard tag when supported by the running server. */
    public void addScoreboardTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return;
        invoke(entity, "addScoreboardTag", new Class<?>[]{String.class}, tag);
    }

    /** Removes the underlying entity. This method is idempotent. */
    public void remove() {
        if (entity.isValid()) entity.remove();
    }

    private double offset() {
        return kind == Kind.ITEM ? HEAD_OFFSET : TEXT_OFFSET;
    }

    private static ArmorStand spawn(Location location) {
        World world = location.getWorld();
        ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setCustomName("");
        stand.setCustomNameVisible(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        invoke(stand, "setCollidable", new Class<?>[]{boolean.class}, false);
        invoke(stand, "setSilent", new Class<?>[]{boolean.class}, true);
        invoke(stand, "setInvulnerable", new Class<?>[]{boolean.class}, true);
        invoke(stand, "setPersistent", new Class<?>[]{boolean.class}, false);
        return stand;
    }

    private static void helmet(ArmorStand stand, ItemStack item) {
        EntityEquipment equipment = stand.getEquipment();
        if (equipment != null) equipment.setHelmet(item);
    }

    private static ItemStack safeItem(ItemStack item, Material fallback) {
        if (item == null || isAir(item.getType())) return new ItemStack(fallback);
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

    private static boolean isAir(Material material) {
        String name = material.name();
        return name.equals("AIR") || name.endsWith("_AIR");
    }

    private static void requireLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Visual location must have a world");
        }
    }

    private static void invoke(Object receiver, String name, Class<?>[] parameters, Object argument) {
        try {
            Method method = receiver.getClass().getMethod(name, parameters);
            method.invoke(receiver, argument);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional server-version capability.
        }
    }

    private enum Kind { ITEM, TEXT }
}
