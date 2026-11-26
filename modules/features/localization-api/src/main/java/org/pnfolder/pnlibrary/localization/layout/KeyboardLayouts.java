package org.pnfolder.pnlibrary.localization.layout;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The keyboard layouts a localization service reads queries against: the {@link KeyboardLayout#builtIn} ones plus any
 * registered here. {@code TranslationIndex.suggest} and {@code best} use them unless the call passes its own layouts.
 *
 * <p>The registry belongs to the localization service, which every plugin shares, so a layout registered by one plugin
 * is seen by all of them. Prefix its id with the plugin name. Through {@code ModuleContext.localization} a layout is
 * removed when the module closes; the returned handle only removes it earlier.
 */
public interface KeyboardLayouts extends AutoCloseable {
    /** The built-in layouts only. It is read-only: {@link #register} throws {@link UnsupportedOperationException}. */
    KeyboardLayouts builtIn = new ReadOnlyKeyboardLayouts();

    /**
     * Adds {@code layout} to {@link #all()}. Throws {@link IllegalArgumentException} when its id is already taken, by
     * a built-in layout or a registered one, and {@link IllegalStateException} after {@link #close()}. Closing the
     * returned handle removes this registration; closing it again does nothing, even if the same layout was
     * registered since.
     *
     * <pre>{@code
     * AutoCloseable handle = context.getLocalization().getLayouts().register(KeyboardLayout.of("myplugin-dvorak", "..."));
     * handle.close(); // optional: closing the module removes it too
     * }</pre>
     */
    @NotNull
    AutoCloseable register(@NotNull KeyboardLayout layout);

    /** The built-in layouts first, then the registered ones in registration order. */
    @NotNull
    List<KeyboardLayout> all();

    /** Removes every registered layout and rejects further registrations. */
    @Override
    void close();

    /** Creates an empty registry holding the built-in layouts. */
    @NotNull
    static KeyboardLayouts create() {
        return new KeyboardLayoutRegistry();
    }
}
