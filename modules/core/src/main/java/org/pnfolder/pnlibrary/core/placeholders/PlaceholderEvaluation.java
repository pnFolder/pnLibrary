package org.pnfolder.pnlibrary.core.placeholders;

import org.pnfolder.pnlibrary.core.placeholders.scan.PhPart;
import org.pnfolder.pnlibrary.core.placeholders.scan.PhNode;
import org.pnfolder.pnlibrary.core.placeholders.address.ParsedArgument;
import org.pnfolder.pnlibrary.core.placeholders.address.ParsedAddress;
import org.pnfolder.pnlibrary.core.placeholders.format.Formatted;
import org.pnfolder.pnlibrary.core.placeholders.format.FormatRegistry;
import org.pnfolder.pnlibrary.core.placeholders.format.FormatDef;
import org.pnfolder.pnlibrary.core.placeholders.format.FormatCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentInput;
import org.pnfolder.pnlibrary.api.placeholders.argument.ArgumentValue;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteKind;
import org.pnfolder.pnlibrary.api.placeholders.route.RoutePart;

/** Walks a placeholder address down the tree, reads its arguments and applies the formats to the answer. */
final class PlaceholderEvaluation {
    private final FormatRegistry formats;
    private final Consumer<String> report;

    PlaceholderEvaluation(FormatRegistry formats, Consumer<String> report) {
        this.formats = formats;
        this.report = report;
    }

    Match find(PhNode node, List<String> words, int index, List<PhNode> path) {
        if (index == words.size()) {
            return node.getValue() != null ? new Match(node, path) : null;
        }
        String word = words.get(index);
        for (PhNode child : node.getChildren()) {
            if (!child.getPart().isSlot() && child.getPart().getName().equalsIgnoreCase(word)) {
                Match found = find(child, words, index + 1, extend(path, child));
                if (found != null) {
                    return found;
                }
            }
        }
        for (PhNode child : node.getChildren()) {
            if (child.getPart().isSlot()) {
                Match found = find(child, words, index + 1, extend(path, child));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<PhNode> extend(List<PhNode> path, PhNode child) {
        List<PhNode> longer = new ArrayList<>(path);
        longer.add(child);
        return longer;
    }

    private RouteInfo routeOf(Match match, ParsedAddress address) {
        // The field is the last word; arguments after it (stats.kills.Alex) belong to it.
        int lastWord = -1;
        for (int i = 0; i < match.getPath().size(); i++) {
            if (!match.getPath().get(i).getPart().isSlot()) {
                lastWord = i;
            }
        }
        List<RoutePart> parts = new ArrayList<>();
        for (int index = 0; index < match.getPath().size(); index++) {
            PhPart part = match.getPath().get(index).getPart();
            String text = address.getWords().get(index);
            RouteKind kind = RouteKind.ARGUMENT;
            if (!part.isSlot()) {
                kind = index == 0 ? RouteKind.GROUP : index == lastWord ? RouteKind.FIELD : RouteKind.BRANCH;
            }
            parts.add(new RoutePart(kind, text, part.getName()));
        }
        for (FormatCall format : address.getFormats()) {
            parts.add(new RoutePart(RouteKind.FORMAT, format.getName(), format.getName(), format.getArguments()));
        }
        return new RouteInfo(parts);
    }

    /** Reads the arguments of the path one after another, left to right; each converter sees the ones before it. */
    private CompletionStage<Map<String, ParsedArgument>> readSlots(Match match, ParsedAddress address, RouteInfo route, ViewerInfo viewer) {
        CompletionStage<Map<String, ParsedArgument>> chain = CompletableFuture.completedFuture(Collections.emptyMap());
        int position = 0;
        for (int index = 0; index < match.getPath().size(); index++) {
            final PhPart part = match.getPath().get(index).getPart();
            if (!part.isSlot()) {
                continue;
            }
            final String text = address.getWords().get(index);
            final int here = position++;
            chain = chain.thenCompose(done -> {
                List<ArgumentValue<?>> previous = new ArrayList<>();
                for (ParsedArgument argument : done.values()) {
                    previous.add(argument.getArgument());
                }
                ArgumentInput input = new ArgumentInput(text, viewer, route, part.getName(), here, previous);
                return part.getType().convert(input).thenApply(parsed -> {
                    Map<String, ParsedArgument> next = new LinkedHashMap<>(done);
                    next.put(part.getName(), parsed);
                    return next;
                });
            });
        }
        return chain;
    }

    CompletionStage<Object> evaluate(Match match, ParsedAddress address, ViewerInfo viewer, String original, String consumer) {
        RouteInfo route = routeOf(match, address);
        return readSlots(match, address, route, viewer).thenCompose(slots -> {
            CompletionStage<Object> stage;
            try {
                stage = match.getNode().getValue().apply(new Call(viewer, slots, route));
            } catch (Throwable failure) {
                report.accept("Placeholder " + original + " failed: " + Failures.describe(failure));
                return CompletableFuture.completedFuture(null);
            }
            return stage.handle((value, failure) -> {
                if (failure != null) {
                    report.accept("Placeholder " + original + " failed: " + Failures.describeWrapped(failure));
                    return null;
                }
                if (value instanceof Formatted) {
                    Formatted formatted = (Formatted) value;
                    return applyFormats(formatted.getValue(), match.getNode(), address.getFormats(), viewer, original, formatted.getFormat(), consumer);
                }
                return applyFormats(value, match.getNode(), address.getFormats(), viewer, original, null, consumer);
            });
        });
    }

    /** Applies the formats written in the address, or else the default format of {@code own} or of the node. */
    Object applyFormats(
            Object value, PhNode node, List<FormatCall> written, ViewerInfo viewer, String original, String own, String consumer) {
        List<FormatCall> chain = chainOf(node, written, own);
        if (value == null) {
            for (FormatCall call : chain) {
                if (call.getName().equals("default")) {
                    return String.join(",", call.getArguments());
                }
            }
            return null;
        }
        Object current = value;
        for (FormatCall call : chain) {
            FormatDef format = formats.find(call.getName(), current, consumer, call.getOwner());
            if (format == null) {
                report.accept("Placeholder " + original + ": no format '" + call.getName() + "' fits " + current.getClass().getSimpleName());
                continue;
            }
            try {
                current = format.getApply().apply(current, call.getArguments(), viewer);
            } catch (Exception failure) {
                report.accept("Placeholder " + original + ": format '" + call.getName() + "' failed: " + Failures.describe(failure));
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static List<FormatCall> chainOf(PhNode node, List<FormatCall> written, String own) {
        List<FormatCall> explicit = new ArrayList<>();
        for (FormatCall call : written) {
            if (!call.getName().equals("raw")) {
                explicit.add(call);
            }
        }
        if (!explicit.isEmpty()) {
            return explicit;
        }
        if (!written.isEmpty()) {
            return Collections.emptyList();
        }
        if (own != null) {
            return Collections.singletonList(new FormatCall(own, Collections.emptyList()));
        }
        if (node != null && node.getFormat() != null) {
            return Collections.singletonList(new FormatCall(node.getFormat(), Collections.emptyList()));
        }
        return Collections.emptyList();
    }
}
