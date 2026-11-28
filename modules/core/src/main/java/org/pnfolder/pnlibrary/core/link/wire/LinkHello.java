package org.pnfolder.pnlibrary.core.link.wire;

import java.time.Instant;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * What one end of the link says about itself: which message formats it speaks and which library it runs. The hello
 * itself is written in a format that never changes, so two ends of any versions can always read each other's hello
 * and tell an administrator that they cannot talk further.
 */
public final class LinkHello {
    /** The role of a proxy. */
    public static final String PROXY = "proxy";
    /** The role of a game server. */
    public static final String SERVER = "server";

    private final String role;
    private final String profile;
    private final String libraryVersion;
    private final Instant sentAt;

    public LinkHello(@NotNull String role, @NotNull String profile, @NotNull String libraryVersion, @NotNull Instant sentAt) {
        this.role = Objects.requireNonNull(role, "role");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.libraryVersion = Objects.requireNonNull(libraryVersion, "libraryVersion");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    }

    /** The hello of this library: it speaks {@link LinkCodec#PROFILE}. */
    public static @NotNull LinkHello current(@NotNull String role, @NotNull String libraryVersion, @NotNull Instant now) {
        return new LinkHello(role, LinkCodec.PROFILE, libraryVersion, now);
    }

    /** {@link #PROXY} or {@link #SERVER}. */
    public @NotNull String getRole() {
        return role;
    }

    /** The versions of all message formats, as one text; two ends talk only when these are equal. */
    public @NotNull String getProfile() {
        return profile;
    }

    public @NotNull String getLibraryVersion() {
        return libraryVersion;
    }

    public @NotNull Instant getSentAt() {
        return sentAt;
    }

    /** Whether this end can exchange data with an end that sent {@code other}. */
    public boolean speaksWith(@NotNull LinkHello other) {
        return profile.equals(other.profile);
    }
}
