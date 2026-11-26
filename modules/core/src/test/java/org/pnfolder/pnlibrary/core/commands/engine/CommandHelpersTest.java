package org.pnfolder.pnlibrary.core.commands.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandHelpersTest {
    @Test
    @DisplayName("page count rounds up and is never below one")
    void pageCountRoundsUpAndIsNeverBelowOne() {
        assertEquals(1, ChatPager.pageCount(0, 10));
        assertEquals(1, ChatPager.pageCount(10, 10));
        assertEquals(2, ChatPager.pageCount(11, 10));
    }

    @Test
    @DisplayName("ready suggestions are returned")
    void readySuggestionsAreReturned() {
        assertEquals(Arrays.asList("a"), TabSuggestions.suggestionsWithin(CompletableFuture.completedFuture(Arrays.asList("a")), 50));
    }

    @Test
    @DisplayName("late or failed suggestions give an empty list")
    void lateOrFailedSuggestionsGiveAnEmptyList() {
        assertTrue(TabSuggestions.suggestionsWithin(new CompletableFuture<>(), 10).isEmpty());
        CompletableFuture<List<String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("x"));
        assertTrue(TabSuggestions.suggestionsWithin(failed, 10).isEmpty());
    }
}
