package org.pnfolder.pnlibrary.localization.layout;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mutable registry guarded by {@code lock}; readers use the volatile snapshot of {@code layouts} without locking. */
final class KeyboardLayoutRegistry implements KeyboardLayouts {
    private final Object lock = new Object();
    private volatile List<KeyboardLayout> layouts = KeyboardLayout.builtIn;
    private boolean closed;

    @NotNull
    @Override
    public AutoCloseable register(@NotNull final KeyboardLayout layout) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("Keyboard layouts are closed");
            }
            for (KeyboardLayout existing : layouts) {
                if (existing.getId().equals(layout.getId())) {
                    throw new IllegalArgumentException("Keyboard layout '" + layout.getId() + "' is already registered");
                }
            }
            List<KeyboardLayout> next = new ArrayList<>(layouts);
            next.add(layout);
            layouts = Collections.unmodifiableList(next);
        }
        final AtomicBoolean done = new AtomicBoolean();
        return new AutoCloseable() {
            @Override
            public void close() {
                if (!done.compareAndSet(false, true)) {
                    return;
                }
                synchronized (lock) {
                    List<KeyboardLayout> next = new ArrayList<>();
                    for (KeyboardLayout existing : layouts) {
                        if (existing != layout) {
                            next.add(existing);
                        }
                    }
                    layouts = Collections.unmodifiableList(next);
                }
            }
        };
    }

    @NotNull
    @Override
    public List<KeyboardLayout> all() {
        return layouts;
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            layouts = KeyboardLayout.builtIn;
        }
    }
}
