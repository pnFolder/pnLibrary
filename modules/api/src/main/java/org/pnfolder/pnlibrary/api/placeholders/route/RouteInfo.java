package org.pnfolder.pnlibrary.api.placeholders.route;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The whole way of the asked address, handed to a parameter marked with {@code @Route}. */
public final class RouteInfo {
    private final List<RoutePart> parts;

    /** Creates the route from its parts in the order written. */
    public RouteInfo(@NotNull List<RoutePart> parts) {
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
    }

    /** Every part in the order written. */
    @NotNull
    public List<RoutePart> getParts() {
        return parts;
    }

    /** The address without formats: {@code clan.war.top.3.name}. */
    @NotNull
    public String getPath() {
        StringBuilder path = new StringBuilder();
        for (RoutePart part : parts) {
            if (part.getKind() == RouteKind.FORMAT) {
                continue;
            }
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(part.getText());
        }
        return path.toString();
    }

    /** The group, the first word. */
    @NotNull
    public String getGroup() {
        return parts.isEmpty() ? "" : parts.get(0).getText();
    }

    /** The field, the last word with the value. */
    @Nullable
    public RoutePart getField() {
        List<RoutePart> fields = of(RouteKind.FIELD);
        return fields.isEmpty() ? null : fields.get(fields.size() - 1);
    }

    /** The branches in the order written: {@code war}, {@code enemy}. */
    @NotNull
    public List<RoutePart> getBranches() {
        return of(RouteKind.BRANCH);
    }

    /** The arguments written in the path, in order: the {@code 3} of {@code top.3}. */
    @NotNull
    public List<RoutePart> getArguments() {
        return of(RouteKind.ARGUMENT);
    }

    /** The formats after the bars, in order: {@code pad0:4}, {@code upper}. */
    @NotNull
    public List<RoutePart> getFormats() {
        return of(RouteKind.FORMAT);
    }

    /** Every part of {@code kind}, in the order written. */
    @NotNull
    public List<RoutePart> of(@NotNull RouteKind kind) {
        List<RoutePart> result = new ArrayList<>();
        for (RoutePart part : parts) {
            if (part.getKind() == kind) {
                result.add(part);
            }
        }
        return result;
    }

    /** The parts after the first part called {@code name}; empty when there is none. A branch {@code war} asks {@code after("war")}. */
    @NotNull
    public List<RoutePart> after(@NotNull String name) {
        for (int index = 0; index < parts.size(); index++) {
            RoutePart part = parts.get(index);
            if (part.getKind() != RouteKind.FORMAT && part.getName().equals(name)) {
                return new ArrayList<>(parts.subList(index + 1, parts.size()));
            }
        }
        return new ArrayList<>();
    }

    /** Whether the address has a format called {@code name}. */
    public boolean hasFormat(@NotNull String name) {
        for (RoutePart part : parts) {
            if (part.getKind() == RouteKind.FORMAT && part.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (RoutePart part : parts) {
            if (text.length() > 0) {
                text.append('.');
            }
            text.append(part.getKind().name().toLowerCase(java.util.Locale.ROOT)).append(':').append(part.getText());
        }
        return text.toString();
    }
}
