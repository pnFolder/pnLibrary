package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.plugin.lifecycle.LifecycleRow;

/**
 * A lifecycle card that lets the plugin hide the rows pnLibrary adds by itself before any of its own rows are added.
 *
 * <p>The automatic rows are known up front; the plugin's rows are recorded and replayed after them, so hiding never
 * touches a plugin's own row. {@link #hide} may be called in the fluent chain and reads as what it does.
 */
public final class HidingLifecycleBox implements MessageBox {
    /** One row the library adds by itself. */
    public static final class AutoRow {
        private final LifecycleRow tag;
        private final boolean notConfigured;
        private final Consumer<MessageBox> write;

        public AutoRow(LifecycleRow tag, boolean notConfigured, Consumer<MessageBox> write) {
            this.tag = tag;
            this.notConfigured = notConfigured;
            this.write = write;
        }
    }

    private final MessageBox delegate;
    private final List<AutoRow> autoRows;
    private final Set<LifecycleRow> hidden = new LinkedHashSet<LifecycleRow>();
    private final List<Consumer<MessageBox>> pluginRows = new ArrayList<Consumer<MessageBox>>();

    public HidingLifecycleBox(MessageBox delegate, List<AutoRow> autoRows) {
        this.delegate = delegate;
        this.autoRows = autoRows;
    }

    @Override
    public @NotNull MessageBox ok(@NotNull final String label, @NotNull final String detail) {
        pluginRows.add(new Consumer<MessageBox>() {
            @Override
            public void accept(MessageBox box) {
                box.ok(label, detail);
            }
        });
        return this;
    }

    @Override
    public @NotNull MessageBox warn(@NotNull final String label, @NotNull final String detail) {
        pluginRows.add(new Consumer<MessageBox>() {
            @Override
            public void accept(MessageBox box) {
                box.warn(label, detail);
            }
        });
        return this;
    }

    @Override
    public @NotNull MessageBox skip(@NotNull final String label, @NotNull final String detail) {
        pluginRows.add(new Consumer<MessageBox>() {
            @Override
            public void accept(MessageBox box) {
                box.skip(label, detail);
            }
        });
        return this;
    }

    @Override
    public @NotNull MessageBox fail(@NotNull final String label, @NotNull final String detail, @Nullable final Throwable error) {
        pluginRows.add(new Consumer<MessageBox>() {
            @Override
            public void accept(MessageBox box) {
                box.fail(label, detail, error);
            }
        });
        return this;
    }

    @Override
    public @NotNull MessageBox hide(@NotNull LifecycleRow... rows) {
        for (LifecycleRow row : rows) {
            hidden.add(row);
        }
        return this;
    }

    @Override
    public void show() {
        for (AutoRow row : autoRows) {
            if (hidden.contains(row.tag)) {
                continue;
            }
            if (hidden.contains(LifecycleRow.NOT_CONFIGURED) && row.notConfigured) {
                continue;
            }
            row.write.accept(delegate);
        }
        for (Consumer<MessageBox> row : pluginRows) {
            row.accept(delegate);
        }
        delegate.show();
    }
}
