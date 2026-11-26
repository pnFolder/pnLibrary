package org.pnfolder.pnlibrary.api.metrics.provider;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.security.SecretValue;

import java.util.function.Function;

/**
 * Provider-specific project credentials.
 *
 * <p>A bStats project ID is public metadata. A FastStats token is sensitive and must not be included in logs or
 * diagnostic reports. Closing credentials releases owned secret buffers.
 *
 * @param <T> the type of the credential value
 */
public abstract class MetricsCredentials<T> implements AutoCloseable {
    private MetricsCredentials() {
    }

    /** Backend identified by these credentials. */
    @NotNull
    public abstract MetricsProvider getProvider();

    /** Provides typed, scoped access to the provider's credential value. */
    public abstract <R> R useValue(@NotNull Function<T, R> operation);

    /** Releases owned resources; public numeric identifiers require no cleanup. */
    @Override
    public void close() {
    }

    /** Public positive project identifier assigned by bStats. */
    public static final class BStats extends MetricsCredentials<Integer> {
        private final int projectId;

        /** Creates credentials for the public bStats project {@code projectId}. */
        public BStats(int projectId) {
            if (projectId <= 0) {
                throw new IllegalArgumentException("Metrics project ID must be positive for bStats");
            }
            this.projectId = projectId;
        }

        /** The public bStats project identifier. */
        public int getProjectId() {
            return projectId;
        }

        @NotNull
        @Override
        public MetricsProvider getProvider() {
            return MetricsProvider.BSTATS;
        }

        @Override
        public <R> R useValue(@NotNull Function<Integer, R> operation) {
            return operation.apply(projectId);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BStats && ((BStats) other).projectId == projectId;
        }

        @Override
        public int hashCode() {
            return projectId;
        }

        @NotNull
        @Override
        public String toString() {
            return "BStats(projectId=" + projectId + ")";
        }
    }

    /**
     * Owns a defensive copy of the FastStats token and erases that copy on close.
     *
     * <p>The caller owns the input array and should erase it separately. Native SDKs may retain immutable strings; this
     * class cannot erase those strings or a caller's original String.
     */
    public static final class FastStats extends MetricsCredentials<char[]> {
        private final SecretValue secret;

        /** Copies {@code token}; it must hold a non-blank character. */
        public FastStats(@NotNull char[] token) {
            if (!hasContent(token)) {
                throw new IllegalArgumentException("FastStats requires a non-blank project token");
            }
            this.secret = new SecretValue(token);
        }

        private static boolean hasContent(char[] token) {
            for (char character : token) {
                if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)) {
                    return true;
                }
            }
            return false;
        }

        @NotNull
        @Override
        public MetricsProvider getProvider() {
            return MetricsProvider.FASTSTATS;
        }

        /** Supplies an isolated token copy and erases it after the operation returns. */
        @Override
        public <R> R useValue(@NotNull Function<char[], R> operation) {
            return secret.useSecret(operation);
        }

        /**
         * Supplies a temporary String at the native SDK boundary.
         *
         * <p>Do not retain or log the supplied value except where required by the SDK. The JVM provides no reliable
         * way to erase the immutable String.
         */
        public <R> R useToken(@NotNull Function<String, R> operation) {
            return useValue(characters -> operation.apply(new String(characters)));
        }

        /** Erases the owned character buffer once and prevents further access. */
        @Override
        public void close() {
            secret.close();
        }

        /** Always redacts the token from textual representations. */
        @NotNull
        @Override
        public String toString() {
            return "FastStats(token=[REDACTED])";
        }
    }
}
