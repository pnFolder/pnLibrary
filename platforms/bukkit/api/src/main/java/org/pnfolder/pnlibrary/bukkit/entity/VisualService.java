package org.pnfolder.pnlibrary.bukkit.entity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Spawns {@link VisualEntity} instances. Obtain it from {@code BukkitPlatform.getVisuals()}. */
public interface VisualService {

    /** Spawns a floating item seen at {@code location}; {@code null} or air shows a nether star. */
    @NotNull VisualEntity item(@NotNull Location location, @Nullable ItemStack item);

    /** Spawns a floating block seen at {@code location}; {@code null} or air shows a chest. */
    @NotNull VisualEntity block(@NotNull Location location, @Nullable Material material);

    /** Spawns a floating label seen at {@code location}; {@code null} shows an empty label. */
    @NotNull VisualEntity text(@NotNull Location location, @Nullable String text);
}
