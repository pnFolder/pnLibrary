package org.pnfolder.pnlibrary.updates.engine.net;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Pattern;

/** Trusted catalog facts used to verify one downloaded artifact. */
public final class ArtifactSpecification {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final ProductId product;
    private final SemanticVersion version;
    private final ApiVersionRange supportedApi;
    private final String fileName;
    private final Long size;
    private final String sha256;

    public ArtifactSpecification(@NotNull ProductId product, @NotNull SemanticVersion version,
                                 @NotNull ApiVersionRange supportedApi, @NotNull String fileName) {
        this(product, version, supportedApi, fileName, null, null);
    }

    /**
     * @param product expected product identity
     * @param version expected product version
     * @param supportedApi declared supported pnLibrary API range
     * @param fileName expected plain filename
     * @param size expected byte length, when published
     * @param sha256 expected SHA-256 digest, when published
     */
    public ArtifactSpecification(@NotNull ProductId product, @NotNull SemanticVersion version,
                                 @NotNull ApiVersionRange supportedApi, @NotNull String fileName,
                                 @Nullable Long size, @Nullable String sha256) {
        if (fileName.trim().isEmpty() || !Paths.get(fileName).getFileName().toString().equals(fileName)) {
            throw new IllegalArgumentException("fileName must be a plain file name");
        }
        if (size != null && size <= 0) {
            throw new IllegalArgumentException("Artifact size must be positive");
        }
        if (sha256 != null && !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("SHA-256 must contain 64 hexadecimal characters");
        }
        this.product = product;
        this.version = version;
        this.supportedApi = supportedApi;
        this.fileName = fileName;
        this.size = size;
        this.sha256 = sha256;
    }

    @NotNull
    public ProductId getProduct() {
        return product;
    }

    @NotNull
    public SemanticVersion getVersion() {
        return version;
    }

    @NotNull
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    @NotNull
    public String getFileName() {
        return fileName;
    }

    @Nullable
    public Long getSize() {
        return size;
    }

    @Nullable
    public String getSha256() {
        return sha256;
    }

    /** The same specification for a file stored under another name. */
    @NotNull
    public ArtifactSpecification withFileName(@NotNull String value) {
        return new ArtifactSpecification(product, version, supportedApi, value, size, sha256);
    }

    /** The same specification with another expected digest. */
    @NotNull
    public ArtifactSpecification withSha256(@Nullable String value) {
        return new ArtifactSpecification(product, version, supportedApi, fileName, size, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ArtifactSpecification)) {
            return false;
        }
        ArtifactSpecification that = (ArtifactSpecification) other;
        return product.equals(that.product) && version.equals(that.version) && supportedApi.equals(that.supportedApi)
            && fileName.equals(that.fileName) && Objects.equals(size, that.size) && Objects.equals(sha256, that.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, version, supportedApi, fileName, size, sha256);
    }

    @Override
    public String toString() {
        return "ArtifactSpecification(product=" + product + ", version=" + version + ", supportedApi=" + supportedApi
            + ", fileName=" + fileName + ", size=" + size + ", sha256=" + sha256 + ")";
    }
}
