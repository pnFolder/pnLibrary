package org.pnfolder.pnlibrary.api.updates;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Input checks shared by the immutable models of updates, dependencies and remote policies. */
@ApiStatus.Internal
public final class UpdateValidation {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private UpdateValidation() {
    }

    /** Fails with "{@code what} SHA-256 is invalid" unless {@code value} is absent or 64 hexadecimal characters. */
    public static void requireSha256(@Nullable String value, @NotNull String what) {
        if (value != null && !SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " SHA-256 is invalid");
        }
    }

    /** Fails with "{@code what} size must be positive" unless {@code value} is absent or above zero. */
    public static void requirePositiveSize(@Nullable Long value, @NotNull String what) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(what + " size must be positive");
        }
    }

    /** Fails unless {@code providesApi} is absent or a positive API generation. */
    public static void requireProvidedApi(@Nullable Integer providesApi) {
        if (providesApi != null && providesApi <= 0) {
            throw new IllegalArgumentException("Provided API generation must be positive");
        }
    }

    /** Whether {@code javaFeature} lies within {@code minimum} and the optional {@code maximum}, both inclusive. */
    public static boolean javaInRange(int javaFeature, int minimum, @Nullable Integer maximum) {
        return javaFeature >= minimum && (maximum == null || javaFeature <= maximum);
    }

    /** Returns an unmodifiable snapshot of {@code list} that later changes to the original cannot reach. */
    @NotNull
    public static <T> List<T> frozen(@NotNull List<? extends T> list) {
        return Collections.unmodifiableList(new ArrayList<T>(list));
    }

    /** Returns whether {@code uri} uses the HTTPS scheme and names a non-blank host. */
    public static boolean isHttps(@NotNull URI uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null && !host.trim().isEmpty();
    }

    /** Parses {@code value} and returns it when it is an HTTPS URI with a host; otherwise fails with {@code message}. */
    @NotNull
    public static URI requireHttpsUri(@NotNull String value, @NotNull String message) {
        URI uri = URI.create(value);
        if (!isHttps(uri)) {
            throw new IllegalArgumentException(message);
        }
        return uri;
    }
}
