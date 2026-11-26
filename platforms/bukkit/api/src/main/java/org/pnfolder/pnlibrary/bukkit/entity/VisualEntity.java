package org.pnfolder.pnlibrary.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A floating item, block or text label for animations and holograms. Create it with {@link VisualService}.
 *
 * <p>{@link #getLocation()} is where the item or text is seen, not where the backing entity stands. Call from the
 * thread that owns the entity (the main thread, or the region thread on Folia).
 */
public interface VisualEntity {

    /** The backing Bukkit entity. */
    @NotNull Entity getEntity();

    /** Whether the visual still exists in the world. */
    boolean isValid();

    /** Where the visual is seen. */
    @NotNull Location getLocation();

    /** Moves the visual; if the server rejects the teleport, {@link #getLocation()} stays as it was. */
    void teleport(@NotNull Location location);

    /** Turns the visual to {@code yaw} and {@code pitch}, in degrees. */
    void setRotation(float yaw, float pitch);

    /** Uses the small model for scales between 0 and 0.72, the normal one otherwise. */
    void setScale(float scale);

    /** Shows a single copy of {@code item}; {@code null} or air shows a chest. */
    void setItem(@Nullable ItemStack item);

    /** Shows {@code material} as a block; {@code null} or air shows a chest. */
    void setBlock(@Nullable Material material);

    /** Replaces the floating label; legacy colour codes are shown as colours. */
    void setText(@Nullable String text);

    /** Adds a scoreboard tag on servers that support them; blank tags are ignored. */
    void addScoreboardTag(@NotNull String tag);

    /** Removes the visual. Safe to repeat. */
    void remove();
}
