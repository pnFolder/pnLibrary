package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** Fills the {@code {address}} placeholders of a text, then hands the text to the outside for the {@code %tokens%} of other systems. */
final class TextRendering {
    /** Reads one address as a consumer asks for it. */
    interface Resolver {
        CompletionStage<Object> resolve(String address, ViewerInfo viewer, Map<String, Object> values, @Nullable String consumer);
    }

    /** Fills the tokens of the outside systems into a finished text. */
    interface Outside {
        String fill(String text, @Nullable UUID playerId);
    }

    private final Resolver resolver;
    private final Outside outside;

    TextRendering(Resolver resolver, Outside outside) {
        this.resolver = resolver;
        this.outside = outside;
    }

    CompletionStage<String> render(String text, ViewerInfo viewer, Map<String, Object> values, @Nullable String consumer) {
        final List<CompletableFuture<String>> pieces = new ArrayList<CompletableFuture<String>>();
        int from = 0;
        while (true) {
            int open = text.indexOf('{', from);
            int close = open < 0 ? -1 : matchingClose(text, open);
            if (open < 0 || close < 0) {
                break;
            }
            if (open > from) {
                pieces.add(CompletableFuture.completedFuture(text.substring(from, open)));
            }
            final String original = text.substring(open, close + 1);
            pieces.add(resolver.resolve(original, viewer, values, consumer).toCompletableFuture()
                    .handle((value, failure) -> value != null ? value.toString() : original));
            from = close + 1;
        }
        if (from < text.length()) {
            pieces.add(CompletableFuture.completedFuture(text.substring(from)));
        }
        return CompletableFuture.allOf(pieces.toArray(new CompletableFuture<?>[0]))
                .thenApply(done -> join(pieces))
                .thenApply(filled -> outside.fill(filled, viewer.getPlayerId()));
    }

    private static String join(List<CompletableFuture<String>> pieces) {
        StringBuilder text = new StringBuilder();
        for (CompletableFuture<String> piece : pieces) {
            text.append(piece.join());
        }
        return text.toString();
    }

    /** The index of the brace that closes the one at {@code open}, with pairs inside skipped; -1 when it is not closed. */
    private static int matchingClose(String text, int open) {
        int depth = 0;
        for (int index = open; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }
}
