package org.pnfolder.pnlibrary.core.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;

/** A viewer that remembers what it was shown and sent. */
final class TestMenuViewer implements MenuViewer {
    final List<Menu> opened = new ArrayList<>();
    final List<Component> sent = new ArrayList<>();
    private final UUID id = UUID.randomUUID();
    private final Set<String> held;

    TestMenuViewer() {
        this(null);
    }

    /** A viewer holding exactly {@code held}; null holds every permission. */
    TestMenuViewer(Set<String> held) {
        this.held = held;
    }

    @Override
    public @NotNull UUID getId() {
        return id;
    }

    @Override
    public @NotNull String getName() {
        return "Admin";
    }

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public @NotNull UpdateActor getActor() {
        return new UpdateActor(UpdateActor.Kind.PLAYER, id.toString(), getName());
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        return held == null || held.contains(permission);
    }

    @Override
    public void open(@NotNull Menu menu) {
        opened.add(menu);
    }

    @Override
    public void close() {
    }

    @Override
    public void send(@NotNull List<? extends Component> lines) {
        sent.addAll(lines);
    }

    @Override
    public void runCommand(@NotNull String command) {
    }

    static Set<String> none() {
        return Collections.emptySet();
    }
}
