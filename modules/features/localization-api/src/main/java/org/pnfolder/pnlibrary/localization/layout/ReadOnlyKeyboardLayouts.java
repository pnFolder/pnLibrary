package org.pnfolder.pnlibrary.localization.layout;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** The built-in layouts only; registering is refused and closing does nothing. */
final class ReadOnlyKeyboardLayouts implements KeyboardLayouts {
    @NotNull
    @Override
    public AutoCloseable register(@NotNull KeyboardLayout layout) {
        throw new UnsupportedOperationException(
                "The built-in layouts are read-only; create a registry with KeyboardLayouts.create()");
    }

    @NotNull
    @Override
    public List<KeyboardLayout> all() {
        return KeyboardLayout.builtIn;
    }

    @Override
    public void close() {
    }
}
