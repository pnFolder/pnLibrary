package org.pnfolder.pnlibrary.api.audienceB2;

import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Who an Audience is, read from the Adventure pointers every platform fills in.
 *
 * <pre>{@code
 * if (who.get(PermissionChecker.POINTER).map(c -> c.test("shop.use")).orElse(false)) {
 *     who.sendMessage(Component.text("Welcome, " + AudienceExtensions.name(who)));
 * }
 * }</pre>
 */
public final class AudienceExtensions {
    private AudienceExtensions() {
    }

    /** The name, or {@code Unknown} when the audience is not one sender. */
    public static @NotNull String name(@NotNull Audience audience) {
        return audience.get(Identity.NAME).orElse("Unknown");
    }

    /** The player's UUID, or {@code null} for the console and for groups. */
    public static @Nullable UUID uniqueId(@NotNull Audience audience) {
        return audience.get(Identity.UUID).orElse(null);
    }

    /** The name shown to others, the nickname where the platform has one; falls back to the name. */
    public static @NotNull Component displayName(@NotNull Audience audience) {
        Component displayName = audience.get(Identity.DISPLAY_NAME).orElse(null);
        return displayName != null ? displayName : Component.text(name(audience));
    }

    /** The client's language, or {@code null} when the platform does not tell. */
    public static @Nullable Locale locale(@NotNull Audience audience) {
        return audience.get(Identity.LOCALE).orElse(null);
    }
}
