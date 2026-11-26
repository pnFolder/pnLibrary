package org.pnfolder.pnlibrary.core.logging;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.MessageBox;

import java.util.ArrayList;
import java.util.List;

/** Collects the rows of one startup or shutdown card and prints them as one block. */
final class StartupBox implements MessageBox {
    private final BoxEnvironment environment;
    private final Object owner;
    private final String title;
    private final BoxKind kind;
    private final String explicitName;
    private final String explicitVersion;
    private final List<BoxRow> rows = new ArrayList<>();
    private boolean shown;

    StartupBox(BoxEnvironment environment, Object owner, String title, BoxKind kind,
               @Nullable String explicitName, @Nullable String explicitVersion) {
        this.environment = environment;
        this.owner = owner;
        this.title = title;
        this.kind = kind;
        this.explicitName = explicitName;
        this.explicitVersion = explicitVersion;
    }

    BoxEnvironment environment() {
        return environment;
    }

    Object owner() {
        return owner;
    }

    String title() {
        return title;
    }

    BoxKind kind() {
        return kind;
    }

    @Nullable String explicitName() {
        return explicitName;
    }

    @Nullable String explicitVersion() {
        return explicitVersion;
    }

    List<BoxRow> rows() {
        return rows;
    }

    @Override
    public @NotNull MessageBox ok(@NotNull String label, @NotNull String detail) {
        return add(new BoxRow("OK", label, detail, null));
    }

    @Override
    public @NotNull MessageBox warn(@NotNull String label, @NotNull String detail) {
        return add(new BoxRow("WARN", label, detail, null));
    }

    @Override
    public @NotNull MessageBox skip(@NotNull String label, @NotNull String detail) {
        return add(new BoxRow("SKIP", label, detail, null));
    }

    @Override
    public @NotNull MessageBox fail(@NotNull String label, @NotNull String detail, @Nullable Throwable error) {
        return add(new BoxRow("FAIL", label, detail, error));
    }

    private MessageBox add(BoxRow row) {
        if (shown) {
            throw new IllegalStateException("MessageBox has already been shown");
        }
        if (row.label.trim().isEmpty()) {
            throw new IllegalArgumentException("MessageBox label must not be blank");
        }
        rows.add(row);
        return this;
    }

    @Override
    public void show() {
        if (shown) {
            throw new IllegalStateException("MessageBox has already been shown");
        }
        shown = true;
        StartupCard card = new StartupCard(this);
        card.header();
        card.details();
        card.result();
        card.support();
    }
}
