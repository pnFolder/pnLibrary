package org.pnfolder.pnlibrary.api.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One immutable context used by policies on every supported proxy/server platform. */
public final class RemotePolicyContext {
    private final ProductInfo product;
    private final PlatformInfo platform;
    private final ServerInfo server;
    private final Map<String, String> values;
    private final List<Object> nativeHandles;

    private RemotePolicyContext(Builder builder) {
        if (builder.product == null) {
            throw new IllegalArgumentException("Remote policy context product is required");
        }
        if (builder.platform == null) {
            throw new IllegalArgumentException("Remote policy context platform is required");
        }
        if (builder.server == null) {
            throw new IllegalArgumentException("Remote policy context server is required");
        }
        this.product = builder.product;
        this.platform = builder.platform;
        this.server = builder.server;
        this.values = Collections.unmodifiableMap(new HashMap<String, String>(builder.values));
        this.nativeHandles = Collections.unmodifiableList(new ArrayList<Object>(builder.nativeHandles));
    }

    /** Creates a new {@link Builder}. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Product being evaluated. */
    @NotNull
    public ProductInfo getProduct() {
        return product;
    }

    /** Active proxy or server platform. */
    @NotNull
    public PlatformInfo getPlatform() {
        return platform;
    }

    /** Server and Minecraft version information. */
    @NotNull
    public ServerInfo getServer() {
        return server;
    }

    /** Application-defined immutable policy values. */
    @NotNull
    public Map<String, String> getValues() {
        return values;
    }

    /** Returns the first native platform handle assignable to {@code type}, if present. */
    @Nullable
    public <T> T nativeHandle(@NotNull Class<T> type) {
        for (Object handle : nativeHandles) {
            if (type.isInstance(handle)) {
                return type.cast(handle);
            }
        }
        return null;
    }

    /** Returns a native handle assignable to {@code type} or fails when unavailable. */
    @NotNull
    public <T> T requireNative(@NotNull Class<T> type) {
        T handle = nativeHandle(type);
        if (handle == null) {
            throw new IllegalArgumentException(
                "Native handle " + type.getName() + " is unavailable on " + platform.getType());
        }
        return handle;
    }

    /** Fluent builder for an immutable {@link RemotePolicyContext}. */
    public static final class Builder {
        private ProductInfo product;
        private PlatformInfo platform;
        private ServerInfo server;
        private final Map<String, String> values = new LinkedHashMap<String, String>();
        private final List<Object> nativeHandles = new ArrayList<Object>();

        private Builder() {
        }

        /** Sets the product the decision is about. */
        @NotNull
        public Builder product(@NotNull ProductInfo value) {
            this.product = value;
            return this;
        }

        /** Sets the platform the server runs on. */
        @NotNull
        public Builder platform(@NotNull PlatformInfo value) {
            this.platform = value;
            return this;
        }

        /** Sets the information about the server. */
        @NotNull
        public Builder server(@NotNull ServerInfo value) {
            this.server = value;
            return this;
        }

        /** Adds one custom value for the policy. */
        @NotNull
        public Builder value(@NotNull String key, @NotNull String value) {
            values.put(normalizedKey(key), value);
            return this;
        }

        /** Adds several custom values for the policy. */
        @NotNull
        public Builder values(@NotNull Map<String, String> value) {
            Map<String, String> normalized = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entry : value.entrySet()) {
                normalized.put(normalizedKey(entry.getKey()), entry.getValue());
            }
            values.putAll(normalized);
            return this;
        }

        /** Makes a platform-native object available for typed policy lookup. */
        @NotNull
        public Builder nativeHandle(@NotNull Object value) {
            nativeHandles.add(value);
            return this;
        }

        /** Validates required fields and creates the immutable context. */
        @NotNull
        public RemotePolicyContext build() {
            return new RemotePolicyContext(this);
        }

        private static String normalizedKey(String key) {
            String normalized = key.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Remote policy context value key must not be blank");
            }
            return normalized;
        }
    }
}
