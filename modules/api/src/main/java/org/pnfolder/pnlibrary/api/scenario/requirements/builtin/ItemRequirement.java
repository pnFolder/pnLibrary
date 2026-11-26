package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigKey;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;
import org.pnfolder.pnlibrary.api.world.ItemMatcher;
import org.pnfolder.pnlibrary.api.world.WorldKeys;

import java.util.Objects;

/**
 * Met when the player carries at least {@code amount} items that fit the description.
 *
 * <p>A platform without player inventories never meets it.
 */
public final class ItemRequirement implements Requirement {
    private final String material;
    private final int amount;
    private final String name;
    @ConfigKey("name-contains")
    private final String nameContains;
    @ConfigKey("lore-contains")
    private final String loreContains;
    private final boolean ignoreCase;

    /** Creates it with every setting at its default. */
    public ItemRequirement() {
        this("", 1, null, null, null, true);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public ItemRequirement(@NotNull String material) {
        this(material, 1, null, null, null, true);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public ItemRequirement(@NotNull String material, int amount) {
        this(material, amount, null, null, null, true);
    }

    /** Creates it with the first 3 settings; the rest keep their defaults. */
    public ItemRequirement(@NotNull String material, int amount, @Nullable String name) {
        this(material, amount, name, null, null, true);
    }

    /** Creates it with the first 4 settings; the rest keep their defaults. */
    public ItemRequirement(@NotNull String material, int amount, @Nullable String name, @Nullable String nameContains) {
        this(material, amount, name, nameContains, null, true);
    }

    /** Creates it with the first 5 settings; the rest keep their defaults. */
    public ItemRequirement(@NotNull String material, int amount, @Nullable String name, @Nullable String nameContains, @Nullable String loreContains) {
        this(material, amount, name, nameContains, loreContains, true);
    }

    /** Creates it with every setting given. */
    public ItemRequirement(@NotNull String material, int amount, @Nullable String name, @Nullable String nameContains, @Nullable String loreContains, boolean ignoreCase) {
        if (amount < 1) {
            throw new IllegalArgumentException("Required item amount must be at least 1");
        }
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.nameContains = nameContains;
        this.loreContains = loreContains;
        this.ignoreCase = ignoreCase;
    }

    /** Platform item name such as {@code diamond}. */
    @NotNull
    public String getMaterial() {
        return material;
    }

    /** Required count, at least one. */
    public int getAmount() {
        return amount;
    }

    /** Full display name the item must have, or {@code null}. */
    @Nullable
    public String getName() {
        return name;
    }

    /** Text the display name must contain, or {@code null}. */
    @Nullable
    public String getNameContains() {
        return nameContains;
    }

    /** Text a lore line must contain, or {@code null}. */
    @Nullable
    public String getLoreContains() {
        return loreContains;
    }

    /** Whether text is compared without case sensitivity. */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        if (material.trim().isEmpty()) {
            return new Verdict.Unmet("no item is configured");
        }
        ItemMatcher matcher = new ItemMatcher(WorldKeys.minecraftKey(material), name, nameContains, loreContains, ignoreCase);
        Integer counted = context.getPlayer().countItems(matcher);
        int have = counted != null ? counted : 0;
        return Verdict.of(have >= amount, "need " + amount + " " + material + ", have " + have);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemRequirement)) {
            return false;
        }
        ItemRequirement that = (ItemRequirement) other;
        return Objects.equals(material, that.material)
                && amount == that.amount
                && Objects.equals(name, that.name)
                && Objects.equals(nameContains, that.nameContains)
                && Objects.equals(loreContains, that.loreContains)
                && ignoreCase == that.ignoreCase;
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, amount, name, nameContains, loreContains, ignoreCase);
    }

    @NotNull
    @Override
    public String toString() {
        return "ItemRequirement(material=" + material + ", amount=" + amount + ", name=" + name + ", nameContains=" + nameContains + ", loreContains=" + loreContains + ", ignoreCase=" + ignoreCase + ")";
    }
}
