package org.pnfolder.pnlibrary.api.updates.product;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Installed component metadata shared by embedded descriptors and explicit registration. */
public final class ProductDescriptor {
    private final ProductId boundId;
    private final SemanticVersion version;
    private final ApiVersionRange supportedApi;
    private final UpdateChannel channel;
    private final int minimumJava;
    private final Integer maximumJava;

    private ProductDescriptor(Builder builder, ProductId boundId) {
        if (builder.version == null) {
            throw new IllegalArgumentException("Product version is required");
        }
        if (builder.supportedApi == null) {
            throw new IllegalArgumentException("Supported pnLibrary API range is required");
        }
        this.boundId = boundId;
        this.version = builder.version;
        this.supportedApi = builder.supportedApi;
        this.channel = builder.channel;
        this.minimumJava = builder.minimumJava;
        this.maximumJava = builder.maximumJava;
    }

    /** Creates a new {@link Builder}. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Descriptor for pnLibrary itself when no generated descriptor is available. */
    @NotNull
    public static ProductDescriptor library(@NotNull String version) {
        return builder()
            .version(version)
            .pnLibraryApi(PnLibraryApi.VERSION, PnLibraryApi.VERSION)
            .build()
            .bindTo(PnLibraryIdentity.PRODUCT_ID);
    }

    /** Product identity assigned during module registration. */
    @NotNull
    public ProductId getId() {
        if (boundId == null) {
            throw new IllegalStateException("Product descriptor must be bound by registerModule before its id is read");
        }
        return boundId;
    }

    /** Whether this descriptor has been bound to a product identity. */
    public boolean isBound() {
        return boundId != null;
    }

    /** Installed semantic version. */
    @NotNull
    public SemanticVersion getVersion() {
        return version;
    }

    /** Inclusive pnLibrary API generations accepted by this product. */
    @NotNull
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    /** Maximum-risk release channel accepted by this product. */
    @NotNull
    public UpdateChannel getChannel() {
        return channel;
    }

    /** Oldest Java feature version supported by this installation. */
    public int getMinimumJava() {
        return minimumJava;
    }

    /** Newest Java feature version supported, or {@code null} when unbounded. */
    @Nullable
    public Integer getMaximumJava() {
        return maximumJava;
    }

    /** Copies this descriptor and binds it to normalized product {@code id}. */
    @NotNull
    public ProductDescriptor bindTo(@NotNull String id) {
        Builder copy = new Builder()
            .version(version.toString())
            .pnLibraryApi(supportedApi.getMinimum(), supportedApi.getMaximum())
            .channel(channel)
            .java(minimumJava, maximumJava);
        return new ProductDescriptor(copy, ProductId.of(id));
    }

    /** Fluent builder for installed product metadata. */
    public static final class Builder {
        private SemanticVersion version;
        private ApiVersionRange supportedApi;
        private UpdateChannel channel = UpdateChannel.STABLE;
        private int minimumJava = 8;
        private Integer maximumJava;

        private Builder() {
        }

        /** Sets the range of pnLibrary API versions the product supports. */
        @NotNull
        public Builder pnLibraryApi(int minimum, int maximum) {
            this.supportedApi = new ApiVersionRange(minimum, maximum);
            return this;
        }

        /** Parses and sets the installed semantic {@code value}. */
        @NotNull
        public Builder version(@NotNull String value) {
            this.version = SemanticVersion.parse(value);
            return this;
        }

        /** Sets the update channel the product follows. */
        @NotNull
        public Builder channel(@NotNull UpdateChannel value) {
            this.channel = value;
            return this;
        }

        /** Sets the supported Java range with no upper bound. */
        @NotNull
        public Builder java(int minimum) {
            return java(minimum, null);
        }

        /** Sets the supported Java range; the maximum is open when {@code null}. */
        @NotNull
        public Builder java(int minimum, @Nullable Integer maximum) {
            if (minimum < 8) {
                throw new IllegalArgumentException("Minimum Java must be at least 8");
            }
            if (maximum != null && maximum < minimum) {
                throw new IllegalArgumentException("Maximum Java must be >= minimum Java");
            }
            this.minimumJava = minimum;
            this.maximumJava = maximum;
            return this;
        }

        /** Validates required metadata and creates an unbound descriptor. */
        @NotNull
        public ProductDescriptor build() {
            return new ProductDescriptor(this, null);
        }
    }
}
