package org.pnfolder.pnlibrary.core.menu.style;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.core.menu.ItemSpec;
import org.pnfolder.pnlibrary.core.menu.MenuBuilder;

/** The buttons and frames every menu screen is built from. */
public final class MenuItems {
    private static final int FOOTER_START = 45;
    private static final short LEGACY_BLACK = 15;
    private static final short LEGACY_PURPLE = 10;
    private static final int SLOTS_PER_ROW = 9;

    private MenuItems() {
    }

    public static @NotNull ItemSpec section(@Nullable String texture, @NotNull String material, @NotNull String label,
                                            @NotNull String description) {
        return section(texture, material, label, description, Collections.emptyList());
    }

    public static @NotNull ItemSpec section(@Nullable String texture, @NotNull String material, @NotNull String label,
                                            @NotNull String description, @NotNull Fact... parameters) {
        return section(texture, material, label, description, Arrays.asList(parameters));
    }

    public static @NotNull ItemSpec section(@Nullable String texture, @NotNull String material, @NotNull String label,
                                            @NotNull String description, @NotNull List<Fact> parameters) {
        return icon(texture, material, label,
            Style.lore(Collections.singletonList(description), parameters, Style.left("открыть")));
    }

    public static @NotNull ItemSpec backItem() {
        return backItem("Назад");
    }

    public static @NotNull ItemSpec backItem(@NotNull String label) {
        return icon(Icons.BACK, "ARROW", "« " + label,
            Style.lore(Collections.emptyList(), Collections.emptyList(), Style.left("вернуться")), Tone.WARN);
    }

    public static @NotNull ItemSpec closeItem() {
        return icon(Icons.BACK, "BARRIER", "Закрыть",
            Style.lore(Collections.emptyList(), Collections.emptyList(), Style.left("закрыть меню")), Tone.DANGER);
    }

    public static @NotNull String menuTitle(@NotNull String text) {
        return MenuColors.legacy('0') + "pnLibrary " + MenuColors.legacy('5') + "» " + MenuColors.legacy('0') + text;
    }

    public static @NotNull MenuBuilder menuFrame(@NotNull MenuBuilder builder, int rows) {
        int last = (rows - 1) * SLOTS_PER_ROW;
        int[] corners = {0, 1, 9, 7, 8, 17, last, last + 1, last - 9, last + 7, last + 8, last - 1};
        for (int slot : corners) {
            builder.slot(slot, accent());
        }
        return builder.fill(pane());
    }

    public static void footerCorners(@NotNull MenuBuilder builder) {
        int end = MenuConstants.FOOTER_END;
        for (int slot : new int[] {FOOTER_START, FOOTER_START + 1, end - 2, end - 1}) {
            builder.slot(slot, accent());
        }
    }

    public static @NotNull ItemSpec pane() {
        return glass("BLACK_STAINED_GLASS_PANE", LEGACY_BLACK);
    }

    public static @NotNull ItemSpec accent() {
        return glass("PURPLE_STAINED_GLASS_PANE", LEGACY_PURPLE);
    }

    public static @NotNull ItemSpec glass(@NotNull String modern, short legacyData) {
        return new ItemSpec(null, modern, "STAINED_GLASS_PANE", " ", Collections.emptyList(), legacyData);
    }

    /** A button of the normal tone: the textured head when the server can make one, otherwise the item. */
    public static @NotNull ItemSpec icon(@Nullable String texture, @NotNull String material, @NotNull String label,
                                         @NotNull List<String> lore) {
        return icon(texture, material, label, lore, null, Tone.NORMAL);
    }

    /** A button whose name colour says what it is for. */
    public static @NotNull ItemSpec icon(@Nullable String texture, @NotNull String material, @NotNull String label,
                                         @NotNull List<String> lore, @NotNull Tone tone) {
        return icon(texture, material, label, lore, null, tone);
    }

    /** A button with an {@code alternative} item for old servers. */
    public static @NotNull ItemSpec icon(@Nullable String texture, @NotNull String material, @NotNull String label,
                                         @NotNull List<String> lore, @Nullable String alternative) {
        return icon(texture, material, label, lore, alternative, Tone.NORMAL);
    }

    /**
     * A button: the textured head {@code texture} when the server can make one, otherwise the item {@code material}
     * (or {@code alternative} on old servers). The name colour says what the button is for.
     */
    public static @NotNull ItemSpec icon(@Nullable String texture, @NotNull String material, @NotNull String label,
                                         @NotNull List<String> lore, @Nullable String alternative, @NotNull Tone tone) {
        return new ItemSpec(texture, material, alternative, Style.name(label, tone), lore);
    }
}
