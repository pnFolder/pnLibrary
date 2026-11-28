package org.pnfolder.pnlibrary.core.updates.catalog;

import org.pnfolder.pnlibrary.core.updates.catalog.commands.UpdateCatalogRoutes;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** What the catalog view tests share: sample data and ways to read rendered lines. */
final class ViewFixtures {
    static final UpdateCatalogRoutes BUKKIT = new UpdateCatalogRoutes("/pn updates", product -> "/pn update " + product, "/pn check");
    static final UpdateCatalogRoutes PROXY = new UpdateCatalogRoutes("/pn update");
    static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    private ViewFixtures() {
    }

    static List<Component> catalog(List<UpdateSnapshot> snapshots, int page, boolean updatesOnly, UpdateCatalogRoutes routes) {
        return UpdateCatalogView.catalog(snapshots, page, updatesOnly, routes, NOW, ZoneOffset.UTC);
    }

    static List<Component> catalog(List<UpdateSnapshot> snapshots, UpdateCatalogRoutes routes) {
        return catalog(snapshots, 1, false, routes);
    }

    static List<Component> history(List<UpdateHistoryEntry> entries, int page, UpdateCatalogRoutes routes, int totalPages, String product) {
        return UpdateCatalogView.history(entries, page, routes, totalPages, product, NOW, ZoneOffset.UTC);
    }

    static List<Component> history(List<UpdateHistoryEntry> entries) {
        return history(entries, 1, BUKKIT, 1, null);
    }

    static UpdateHistoryEntry event(String at, UpdateState state, String operation, String actor, String product, String from, String to) {
        return new UpdateHistoryEntry(UUID.randomUUID(), Instant.parse(at), UUID.randomUUID(), state,
            new UpdateActor(UpdateActor.Kind.CONSOLE, actor.toLowerCase(Locale.ROOT), actor), operation,
            Collections.singletonList(new ProductChange(ProductId.of(product), SemanticVersion.parse(from), SemanticVersion.parse(to))), null);
    }

    static UpdateSnapshot snapshot(String product, UpdateState state) {
        return snapshot(product, state, null, UpdateChannel.STABLE, "1.1.0");
    }

    static UpdateSnapshot snapshot(String product, UpdateState state, String changelog) {
        return snapshot(product, state, changelog, UpdateChannel.STABLE, "1.1.0");
    }

    static UpdateSnapshot snapshot(String product, UpdateState state, String changelog, UpdateChannel channel, String latest) {
        return new UpdateSnapshot(product, "1.0.0", latest, channel, state, 17, 8, false, null, null, changelog, null,
            Collections.emptyList(), Collections.emptyList(), null, null);
    }

    /** The bold title of a header line, which is either the line itself or its first child. */
    static Component titleOf(Component header) {
        return header.hasDecoration(TextDecoration.BOLD) ? header : header.children().get(0);
    }

    static Component row(List<Component> lines, String containing) {
        for (Component line : lines) {
            if (plain(line).contains(containing)) {
                return line;
            }
        }
        throw new java.util.NoSuchElementException(containing);
    }

    static String text(List<Component> lines) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            text.append(index == 0 ? "" : "\n").append(plain(lines.get(index)));
        }
        return text.toString();
    }

    static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    static List<String> plains(List<Component> lines) {
        List<String> plains = new ArrayList<>();
        for (Component line : lines) {
            plains.add(plain(line));
        }
        return plains;
    }

    /** Commands a click runs right away, unlike suggestions that only fill the chat box. */
    static List<String> runs(Component component) {
        List<String> runs = new ArrayList<>();
        if (component.clickEvent() != null && component.clickEvent().action() == ClickEvent.Action.RUN_COMMAND) {
            runs.add(component.clickEvent().value());
        }
        for (Component child : component.children()) {
            runs.addAll(runs(child));
        }
        return runs;
    }

    static List<String> clicks(Component component) {
        List<String> clicks = new ArrayList<>();
        if (component.clickEvent() != null) {
            clicks.add(component.clickEvent().value());
        }
        for (Component child : component.children()) {
            clicks.addAll(clicks(child));
        }
        return clicks;
    }

    static List<String> clicks(List<Component> lines) {
        List<String> clicks = new ArrayList<>();
        for (Component line : lines) {
            clicks.addAll(clicks(line));
        }
        return clicks;
    }

    static List<String> runs(List<Component> lines) {
        List<String> runs = new ArrayList<>();
        for (Component line : lines) {
            runs.addAll(runs(line));
        }
        return runs;
    }

    static String hover(Component component) {
        if (component.hoverEvent() == null) {
            return "";
        }
        assertTrue(component.hoverEvent().action() == HoverEvent.Action.SHOW_TEXT);
        Object value = component.hoverEvent().value();
        return value instanceof Component ? plain((Component) value) : "";
    }

    static List<Component> join(List<Component>... parts) {
        List<Component> all = new ArrayList<>();
        for (List<Component> part : parts) {
            all.addAll(part);
        }
        return all;
    }
}
