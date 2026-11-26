package org.pnfolder.pnlibrary.core.placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;

/** Finds the placeholder an address names, reads it for a consumer and gives its value with the formats applied. */
final class PlaceholderResolution {
    private final PlaceholderGroups groups;
    private final AdapterPublications adapters;
    private final PlaceholderEvaluation evaluation;
    private final Consumer<String> report;

    PlaceholderResolution(PlaceholderGroups groups, AdapterPublications adapters, PlaceholderEvaluation evaluation, Consumer<String> report) {
        this.groups = groups;
        this.adapters = adapters;
        this.evaluation = evaluation;
        this.report = report;
    }

    /**
     * Reads {@code address} for {@code consumer}. With a {@code prefix} only the placeholders shown under that
     * PlaceholderAPI prefix answer.
     */
    CompletionStage<Object> resolve(String address, ViewerInfo viewer, Map<String, Object> values, @Nullable String prefix, @Nullable String consumer) {
        if (prefix == null) {
            CompletionStage<Object> nested = resolveNested(address, viewer, values, consumer);
            if (nested != null) {
                return nested;
            }
        }
        ParsedAddress parsed = Addresses.parse(address);
        if (parsed == null) {
            return done(null);
        }
        if (prefix == null && !values.isEmpty() && parsed.getOwner() == null) {
            Object own = callerValue(values, String.join(".", parsed.getWords()), address);
            if (own != null) {
                return done(evaluation.applyFormats(own, null, parsed.getFormats(), viewer, address, null, consumer));
            }
        }
        return resolveRegistered(parsed, address, viewer, prefix, consumer);
    }

    /** The answer for an address that holds {@code {inner}} placeholders; {@code null} when it holds none. */
    private @Nullable CompletionStage<Object> resolveNested(
            String address, final ViewerInfo viewer, final Map<String, Object> values, final String consumer) {
        String trimmed = address.trim();
        boolean braced = trimmed.startsWith("{") && trimmed.endsWith("}");
        String body = braced ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
        if (Addresses.depth(body) > Addresses.MAX_NESTING) {
            report.accept("Placeholder {" + body.substring(0, Math.min(40, body.length())) + "...} holds more than "
                    + Addresses.MAX_NESTING + " placeholders inside each other");
            return done(null);
        }
        List<AddressSegment> segments = Addresses.innerParts(body);
        if (segments == null) {
            return null;
        }
        return expandInner(segments, viewer, values, consumer).thenCompose(filled ->
                filled == null ? done(null) : resolve(filled, viewer, values, null, consumer));
    }

    private static @Nullable Object callerValue(Map<String, Object> values, String path, String address) {
        Object own = values.get(path);
        if (own != null) {
            return own;
        }
        String key = address.startsWith("{") ? address.substring(1) : address;
        key = key.endsWith("}") ? key.substring(0, key.length() - 1) : key;
        int bar = key.indexOf('|');
        return values.get((bar < 0 ? key : key.substring(0, bar)).trim());
    }

    private CompletionStage<Object> resolveRegistered(
            ParsedAddress parsed, String address, ViewerInfo viewer, @Nullable String prefix, @Nullable String consumer) {
        String path = String.join(".", parsed.getWords());
        PlaceholderEntry hiddenEntry = null;
        PhNode hiddenNode = null;
        for (PlaceholderEntry entry : groups.visibleEntries(parsed.getWords().get(0).toLowerCase(Locale.ROOT), consumer)) {
            Match match = matchIn(entry, parsed, prefix, consumer);
            if (match == null) {
                continue;
            }
            if (reachable(entry.getOwner(), match.getNode(), consumer)) {
                return evaluation.evaluate(match, parsed, viewer, address, consumer);
            }
            if (hiddenEntry == null) {
                hiddenEntry = entry;
                hiddenNode = match.getNode();
            }
        }
        if (consumer != null && hiddenEntry != null) {
            report.accept("Placeholder {" + path + "} of " + hiddenEntry.getOwner() + " is not visible to " + consumer + " (visibility: "
                    + hiddenNode.getVisibility().name().toLowerCase(Locale.ROOT) + ")");
        }
        return prefix != null ? done(null) : fromAdapters(viewer, path);
    }

    private CompletionStage<Object> fromAdapters(ViewerInfo viewer, String path) {
        Object outside = adapters.fromAdapters(viewer.getPlayerId(), path);
        if (outside == null) {
            report.accept("Placeholder {" + path + "} is not registered");
        }
        return done(outside);
    }

    private @Nullable Match matchIn(PlaceholderEntry entry, ParsedAddress parsed, @Nullable String prefix, @Nullable String consumer) {
        if (prefix != null && !prefix.equals(entry.getPapi())) {
            return null;
        }
        if (!Ownership.ownerFits(parsed.getOwner(), entry.getOwner(), consumer)) {
            return null;
        }
        return evaluation.find(entry.getRoot(), parsed.getWords(), 1, Collections.singletonList(entry.getRoot()));
    }

    /** Whether the consumer may use the placeholder at {@code node} of an entry of {@code owner}. A null consumer is the outside world. */
    private static boolean reachable(String owner, PhNode node, @Nullable String consumer) {
        return Ownership.nearness(owner, node.getVisibility(), consumer) != null;
    }

    /** Whether {@code address} names a placeholder that {@code consumer} may use; nothing is evaluated. */
    boolean contains(String address, @Nullable String consumer) {
        ParsedAddress parsed = Addresses.parse(address);
        if (parsed == null) {
            return false;
        }
        for (PlaceholderEntry entry : groups.visibleEntries(parsed.getWords().get(0).toLowerCase(Locale.ROOT), consumer)) {
            Match match = matchIn(entry, parsed, null, consumer);
            if (match != null && reachable(entry.getOwner(), match.getNode(), consumer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fills the {@code {inner}} placeholders of an address, as in {@code {clan.of.{player.name}.level}}: each one is read
     * first and its value, quoted when needed, takes its place. {@code null} when one of them gives no value.
     */
    private CompletionStage<String> expandInner(List<AddressSegment> segments, ViewerInfo viewer, Map<String, Object> values, String consumer) {
        final List<CompletableFuture<String>> parts = new ArrayList<CompletableFuture<String>>();
        for (AddressSegment segment : segments) {
            if (!segment.isInner()) {
                parts.add(CompletableFuture.completedFuture(segment.getText()));
                continue;
            }
            parts.add(resolve(segment.getText(), viewer, values, null, consumer).toCompletableFuture()
                    .handle((value, failure) -> value == null ? null : Addresses.quote(value.toString())));
        }
        return CompletableFuture.allOf(parts.toArray(new CompletableFuture<?>[0])).thenApply(ignored -> {
            StringBuilder text = new StringBuilder();
            for (CompletableFuture<String> part : parts) {
                String piece = part.join();
                if (piece == null) {
                    return null;
                }
                text.append(piece);
            }
            return text.toString();
        });
    }

    private static CompletionStage<Object> done(@Nullable Object value) {
        return CompletableFuture.completedFuture(value);
    }
}
