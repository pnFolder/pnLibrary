package org.pnfolder.pnlibrary.api.audienceB2;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the Adventure {@link Pointers} a platform returns from {@code pointers()}. Values given as functions are
 * read when asked for, so a renamed player shows the new name.
 *
 * <pre>{@code
 * public Pointers pointers() {
 *     return AudiencePointers.builder()
 *         .name(() -> handle.getName())
 *         .uniqueId(handle.getUniqueId())
 *         .permission(handle::hasPermission)
 *         .build();
 * }
 * }</pre>
 */
public final class AudiencePointers {
    private AudiencePointers() {
    }

    /** A new builder. */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /** Collects the pointers; only the name and the permission check are required. */
    public static final class Builder {
        private Supplier<String> name;
        private Predicate<String> permission;
        private final Pointers.Builder pointers = Pointers.builder();

        private Builder() {
        }

        /** The name, read when asked for. */
        public @NotNull Builder name(@NotNull Supplier<String> name) {
            this.name = name;
            return this;
        }

        /** The UUID; leave it out for the console. */
        public @NotNull Builder uniqueId(@NotNull UUID uniqueId) {
            pointers.withStatic(Identity.UUID, uniqueId);
            return this;
        }

        /** The nickname shown to others, read when asked for; {@code null} falls back to the name. */
        public @NotNull Builder displayName(@NotNull Supplier<Component> displayName) {
            pointers.withDynamic(Identity.DISPLAY_NAME, displayName);
            return this;
        }

        /** The client's language, read when asked for; {@code null} when the platform cannot tell. */
        public @NotNull Builder locale(@NotNull Supplier<Locale> locale) {
            pointers.withDynamic(Identity.LOCALE, locale);
            return this;
        }

        /** Whether the sender holds a permission. */
        public @NotNull Builder permission(@NotNull Predicate<String> permission) {
            this.permission = permission;
            return this;
        }

        /** Builds the pointers. */
        public @NotNull Pointers build() {
            if (name == null) {
                throw new IllegalArgumentException("name is required");
            }
            if (permission == null) {
                throw new IllegalArgumentException("permission is required");
            }
            Predicate<String> check = permission;
            return pointers
                .withDynamic(Identity.NAME, name)
                .withStatic(PermissionChecker.POINTER, (PermissionChecker) node -> TriState.byBoolean(check.test(node)))
                .build();
        }
    }
}
