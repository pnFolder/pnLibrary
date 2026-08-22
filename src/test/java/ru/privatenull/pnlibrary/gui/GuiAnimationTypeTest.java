package ru.privatenull.pnlibrary.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiAnimationTypeTest {

    @Test
    void parsesFriendlyConfigurationNames() {
        assertEquals(GuiAnimationType.CENTER_OUT, GuiAnimationType.fromConfig("main"));
        assertEquals(GuiAnimationType.LEFT_TO_RIGHT, GuiAnimationType.fromConfig("left-to-right"));
        assertEquals(GuiAnimationType.RIGHT_TO_LEFT, GuiAnimationType.fromConfig("rtl"));
        assertEquals(GuiAnimationType.DIAGONAL_UP, GuiAnimationType.fromConfig("diagonal_up"));
        assertEquals(GuiAnimationType.NONE, GuiAnimationType.fromConfig("off"));
    }

    @Test
    void resolvesConfiguredSectionsInsteadOfHardCodedSlots() {
        GuiAnimationProfile profile = new GuiAnimationProfile(
                GuiAnimationType.CENTER_OUT,
                GuiAnimationType.RIGHT_TO_LEFT, java.util.List.of(0, 3, 9, 12),
                GuiAnimationType.LEFT_TO_RIGHT, java.util.List.of(5, 8, 14, 17),
                GuiAnimationType.DIAGONAL_DOWN);
        assertEquals(GuiAnimationType.CENTER_OUT, profile.resolve(-1));
        assertEquals(GuiAnimationType.RIGHT_TO_LEFT, profile.resolve(12));
        assertEquals(GuiAnimationType.LEFT_TO_RIGHT, profile.resolve(14));
        assertEquals(GuiAnimationType.DIAGONAL_DOWN, profile.resolve(53));
    }
}
