package org.pnfolder.pnlibrary.api.placeholders.argument;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.placeholders.info.ViewerInfo;
import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Everything a converter knows when it turns one part of an address into a value. */
public final class ArgumentInput {
    private final String text;
    private final ViewerInfo viewer;
    private final RouteInfo route;
    private final String name;
    private final int position;
    private final List<ArgumentValue<?>> previous;

    /**
     * Creates the input.
     *
     * @param text what was written, such as {@code Wolves}
     * @param viewer who asks
     * @param route the whole asked address, every part with its kind
     * @param name the name of the parameter that receives the value
     * @param position the place of this argument among the arguments of the address, from 0
     * @param previous the arguments already read to the left of this one
     */
    public ArgumentInput(@NotNull String text, @NotNull ViewerInfo viewer, @NotNull RouteInfo route, @NotNull String name,
                         int position, @NotNull List<ArgumentValue<?>> previous) {
        this.text = Objects.requireNonNull(text);
        this.viewer = Objects.requireNonNull(viewer);
        this.route = Objects.requireNonNull(route);
        this.name = Objects.requireNonNull(name);
        this.position = position;
        this.previous = Collections.unmodifiableList(new ArrayList<>(previous));
    }

    /** What was written. */
    @NotNull
    public String getText() {
        return text;
    }

    /** Who asks. */
    @NotNull
    public ViewerInfo getViewer() {
        return viewer;
    }

    /** The whole asked address. */
    @NotNull
    public RouteInfo getRoute() {
        return route;
    }

    /** The name of the parameter that receives the value. */
    @NotNull
    public String getName() {
        return name;
    }

    /** The place of this argument among the arguments of the address, from 0. */
    public int getPosition() {
        return position;
    }

    /** The arguments already read to the left of this one. */
    @NotNull
    public List<ArgumentValue<?>> getPrevious() {
        return previous;
    }

    /**
     * Says why the text does not fit: always throws. The reason is shown by {@code /pn placeholders test} and kept in
     * {@link ArgumentValue#getError()}. The type parameter lets it stand where a value is expected:
     * {@code return clan != null ? clan : input.fail("no such clan")}.
     */
    @NotNull
    public <R> R fail(@NotNull String reason) {
        throw new ArgumentRejected(reason);
    }
}
