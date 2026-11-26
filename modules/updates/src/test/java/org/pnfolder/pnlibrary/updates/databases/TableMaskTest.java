package org.pnfolder.pnlibrary.updates.databases;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TableMaskTest {
    @Test
    void anEmptyListTakesEverythingAndAStarStandsForAnyText() {
        assertTrue(TableMask.matches(Collections.emptyList(), "anything"));
        assertTrue(TableMask.matches(Arrays.asList("clans", "log_*"), "log_2026_11"));
        assertTrue(TableMask.matches(Arrays.asList("clans"), "clans"));
        assertFalse(TableMask.matches(Arrays.asList("clans"), "clans_old"));
        assertFalse(TableMask.matches(Arrays.asList("log_*"), "blog_1"));
    }

    @Test
    void regexCharactersInAMaskArePlainText() {
        assertTrue(TableMask.matches(Arrays.asList("a.b"), "a.b"));
        assertFalse(TableMask.matches(Arrays.asList("a.b"), "axb"));
        assertTrue(TableMask.matches(Arrays.asList("t(1)+*"), "t(1)+rest"));
        assertTrue(TableMask.matches(Arrays.asList("*"), ""));
    }
}
