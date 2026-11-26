package org.pnfolder.pnlibrary.core.placeholders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.registry.Visibility;

/** Who may use what: the nearness of an owner to a consumer and the owner written in an address. */
public final class Ownership {
    /** A consumer that sees every placeholder, hidden ones too: for the {@code test} command of an operator. */
    public static final String EVERYTHING = "*";

    private Ownership() {
    }

    /**
     * How near something is to the module {@code consumer}, or {@code null} when it may not use it: 0 its own module,
     * 1 its own plugin, 2 everyone else. {@code owner} and {@code consumer} are {@code plugin/module}; a {@code null}
     * consumer (PlaceholderAPI, the library) sees only {@link Visibility#ALL}.
     */
    public static @Nullable Integer nearness(@NotNull String owner, @NotNull Visibility visibility, @Nullable String consumer) {
        if (EVERYTHING.equals(consumer)) {
            return 2;
        }
        if (consumer != null && owner.equals(consumer)) {
            return 0;
        }
        if (visibility == Visibility.MODULE) {
            return null;
        }
        if (consumer != null && pluginOf(owner).equals(pluginOf(consumer))) {
            return 1;
        }
        if (visibility == Visibility.PLUGIN) {
            return null;
        }
        return 2;
    }

    /**
     * Does the owner written in an address fit {@code owner}? Nothing written fits all. {@code ~} is the own plugin of
     * {@code consumer}, {@code ~~} its own module, {@code ~~~} the library, {@code plugin/module} that module,
     * {@code plugin} any module of that plugin.
     */
    public static boolean ownerFits(@Nullable String written, @NotNull String owner, @Nullable String consumer) {
        if (written == null) {
            return true;
        }
        switch (written) {
            case "~~~":
                return owner.isEmpty() || owner.startsWith("pnlibrary/");
            case "~~":
                return consumer != null && owner.equals(consumer);
            case "~":
                return consumer != null && pluginOf(owner).equals(pluginOf(consumer));
            default:
                return written.indexOf('/') >= 0 ? owner.equalsIgnoreCase(written) : pluginOf(owner).equalsIgnoreCase(written);
        }
    }

    private static String pluginOf(String owner) {
        int slash = owner.indexOf('/');
        return slash < 0 ? owner : owner.substring(0, slash);
    }
}
