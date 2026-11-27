package org.pnfolder.pnlibrary.api.audience;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Who an Audience is, read from the Adventure pointers every platform fills in. The console has a name but no UUID.
 *
 * <pre>{@code
 * if (AudienceExtensions.hasPermission(who, "shop.use")) {
 *     who.sendMessage(Component.text("Welcome, " + AudienceExtensions.name(who)));
 * }
 * }</pre>
 */
public final class AudienceExtensions {
    private AudienceExtensions() {
    }

    /** The current human-readable name, or {@code Unknown} when the audience is not one sender. */
    public static @NotNull String name(@NotNull Audience audience) {
        return audience.get(Identity.NAME).orElse("Unknown");
    }

    /** Whether this is a connected player. */
    public static boolean isPlayer(@NotNull Audience audience) {
        return audience.get(Identity.UUID).isPresent();
    }

    /** Whether this is the server console: a sender that is not a player. */
    public static boolean isConsole(@NotNull Audience audience) {
        return !isPlayer(audience);
    }

    /** The stable platform-independent identifier: the player UUID as text, or the name for the console. */
    public static @NotNull String id(@NotNull Audience audience) {
        UUID uniqueId = audience.get(Identity.UUID).orElse(null);
        return uniqueId != null ? uniqueId.toString() : name(audience);
    }

    /** Whether the platform grants this audience {@code permission}; {@code false} when it cannot tell. */
    public static boolean hasPermission(@NotNull Audience audience, @NotNull String permission) {
        PermissionChecker checker = audience.get(PermissionChecker.POINTER).orElse(null);
        return checker != null && checker.test(permission);
    }

    /** The name shown to others, the nickname on platforms that have one; falls back to {@link #name(Audience)}. */
    public static @NotNull Component displayName(@NotNull Audience audience) {
        Component displayName = audience.get(Identity.DISPLAY_NAME).orElse(null);
        return displayName != null ? displayName : Component.text(name(audience));
    }

    /** The language of the client, or {@code null} when the platform does not tell. */
    public static @Nullable Locale locale(@NotNull Audience audience) {
        return audience.get(Identity.LOCALE).orElse(null);
    }

    /**
     * Pointers for a sender: its name, its UUID when it is a player, and a permission check.
     * Platform modules give them back from {@code pointers()}.
     */
    public static @NotNull Pointers senderPointers(@NotNull String name, @Nullable UUID uniqueId,
                                                   @NotNull Predicate<String> permission) {
        return senderPointers(() -> name, uniqueId, permission);
    }

    /** The same for a sender without a UUID, such as the console. */
    public static @NotNull Pointers senderPointers(@NotNull String name, @NotNull Predicate<String> permission) {
        return senderPointers(name, null, permission);
    }

    /** The same with a name that is read when asked for, for senders whose name can change. */
    public static @NotNull Pointers senderPointers(@NotNull Supplier<String> name, @Nullable UUID uniqueId,
                                                   @NotNull Predicate<String> permission) {
        return pointers(name, uniqueId, permission).build();
    }

    /** The same with a name that is read when asked for and no UUID. */
    public static @NotNull Pointers senderPointers(@NotNull Supplier<String> name, @NotNull Predicate<String> permission) {
        return senderPointers(name, null, permission);
    }

    /**
     * Pointers for a player: everything {@code senderPointers} gives, plus the nickname and the client's language.
     * Both are read when asked for; return {@code null} when the platform cannot tell.
     */
    public static @NotNull Pointers playerPointers(@NotNull Supplier<String> name, @NotNull UUID uniqueId,
                                                   @NotNull Supplier<Component> displayName,
                                                   @NotNull Supplier<Locale> locale,
                                                   @NotNull Predicate<String> permission) {
        Pointers.Builder builder = pointers(name, uniqueId, permission);
        builder.withDynamic(Identity.DISPLAY_NAME, displayName);
        builder.withDynamic(Identity.LOCALE, locale);
        return builder.build();
    }

    private static Pointers.Builder pointers(Supplier<String> name, @Nullable UUID uniqueId,
                                             Predicate<String> permission) {
        Pointers.Builder builder = Pointers.builder();
        builder.withDynamic(Identity.NAME, name);
        if (uniqueId != null) {
            builder.withStatic(Identity.UUID, uniqueId);
        }
        builder.withStatic(PermissionChecker.POINTER, (PermissionChecker) node -> TriState.byBoolean(permission.test(node)));
        return builder;
    }

    /**
     * An audience of whoever {@code receivers} returns now, asked again for every operation: "all online players"
     * whose membership changes between two actions.
     */
    public static @NotNull Audience dynamicAudience(@NotNull Supplier<? extends Iterable<? extends Audience>> receivers) {
        return new ForwardingAudience() {
            @Override
            public @NotNull Iterable<? extends Audience> audiences() {
                return receivers.get();
            }
        };
    }
}
