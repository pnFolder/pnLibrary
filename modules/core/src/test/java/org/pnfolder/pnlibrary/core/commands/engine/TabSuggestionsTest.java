package org.pnfolder.pnlibrary.core.commands.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TabSuggestionsTest {
    @Test
    @DisplayName("ready suggestions are returned")
    void readySuggestionsAreReturned() {
        assertEquals(Arrays.asList("a"), TabSuggestions.suggestionsWithin(CompletableFuture.completedFuture(Arrays.asList("a")), 10));
    }

    @Test
    @DisplayName("late suggestions give an empty list")
    void lateSuggestionsGiveAnEmptyList() {
        assertEquals(Collections.<String>emptyList(), TabSuggestions.suggestionsWithin(new CompletableFuture<List<String>>(), 10));
    }

    @Test
    @DisplayName("failed suggestions give an empty list")
    void failedSuggestionsGiveAnEmptyList() {
        CompletableFuture<List<String>> failed = new CompletableFuture<List<String>>();
        failed.completeExceptionally(new IllegalStateException("x"));
        assertEquals(Collections.<String>emptyList(), TabSuggestions.suggestionsWithin(failed, 10));
    }
}
