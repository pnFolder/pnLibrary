package ru.privatenull.pnlibrary.gui;

import java.util.Collection;
import java.util.stream.IntStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A reusable GUI transition profile. Slot groups are deliberately supplied by
 * the consuming plugin because every interface can have a different layout.
 */
public final class GuiAnimationProfile {
    private final GuiAnimationType main;
    private final GuiAnimationType leftSection;
    private final GuiAnimationType rightSection;
    private final GuiAnimationType fallback;
    private final Set<Integer> leftSlots;
    private final Set<Integer> rightSlots;

    /** Standard PrivateNull GUI navigation profile managed by pnLibrary. */
    public static GuiAnimationProfile standard() {
        return new GuiAnimationProfile(
                GuiAnimationType.CENTER_OUT,
                GuiAnimationType.RIGHT_TO_LEFT,
                columns(0, 3),
                GuiAnimationType.LEFT_TO_RIGHT,
                columns(5, 8),
                GuiAnimationType.CENTER_OUT);
    }

    public GuiAnimationProfile(GuiAnimationType main,
                               GuiAnimationType leftSection, Collection<Integer> leftSlots,
                               GuiAnimationType rightSection, Collection<Integer> rightSlots,
                               GuiAnimationType fallback) {
        this.main = type(main);
        this.leftSection = type(leftSection);
        this.rightSection = type(rightSection);
        this.fallback = type(fallback);
        this.leftSlots = slots(leftSlots);
        this.rightSlots = slots(rightSlots);
    }

    public GuiAnimationType resolve(int sourceSlot) {
        if (sourceSlot < 0) return main;
        if (leftSlots.contains(sourceSlot)) return leftSection;
        if (rightSlots.contains(sourceSlot)) return rightSection;
        return fallback;
    }

    public GuiAnimationType main() {
        return main;
    }

    public Set<Integer> leftSlots() {
        return leftSlots;
    }

    public Set<Integer> rightSlots() {
        return rightSlots;
    }

    private static GuiAnimationType type(GuiAnimationType type) {
        return type == null ? GuiAnimationType.CENTER_OUT : type;
    }

    private static Set<Integer> slots(Collection<Integer> slots) {
        if (slots == null) return Set.of();
        return slots.stream().filter(slot -> slot != null && slot >= 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<Integer> columns(int first, int last) {
        return IntStream.range(0, 54)
                .filter(slot -> slot % 9 >= first && slot % 9 <= last)
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }
}
