package org.pnfolder.pnlibrary.api.updates.state;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Structured explanation for a candidate or complete plan that cannot be installed. */
public abstract class BlockedReason {
    private BlockedReason() {
    }

    /** Product cannot run against the API generation required by the graph. */
    public static final class ApiMismatch extends BlockedReason {
        private final ProductId product;
        private final ApiVersionRange supportedApi;
        private final int requiredApi;
        private final String repository;

        /**
         * Creates the reason.
         *
         * @param product incompatible product
         * @param supportedApi API generations accepted by the product
         * @param requiredApi API generation required by the candidate graph
         * @param repository source repository, when known
         */
        public ApiMismatch(@NotNull ProductId product, @NotNull ApiVersionRange supportedApi, int requiredApi,
                           @Nullable String repository) {
            this.product = product;
            this.supportedApi = supportedApi;
            this.requiredApi = requiredApi;
            this.repository = repository;
        }

        /** Creates the reason without a known repository. */
        public ApiMismatch(@NotNull ProductId product, @NotNull ApiVersionRange supportedApi, int requiredApi) {
            this(product, supportedApi, requiredApi, null);
        }

        /** Incompatible product. */
        @NotNull
        public ProductId getProduct() {
            return product;
        }

        /** API generations accepted by the product. */
        @NotNull
        public ApiVersionRange getSupportedApi() {
            return supportedApi;
        }

        /** API generation required by the candidate graph. */
        public int getRequiredApi() {
            return requiredApi;
        }

        /** Source repository, when known. */
        @Nullable
        public String getRepository() {
            return repository;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ApiMismatch)) {
                return false;
            }
            ApiMismatch that = (ApiMismatch) other;
            return requiredApi == that.requiredApi && product.equals(that.product)
                && supportedApi.equals(that.supportedApi) && Objects.equals(repository, that.repository);
        }

        @Override
        public int hashCode() {
            return Objects.hash(product, supportedApi, requiredApi, repository);
        }

        @Override
        public String toString() {
            return "ApiMismatch(product=" + product + ", supportedApi=" + supportedApi
                + ", requiredApi=" + requiredApi + ", repository=" + repository + ")";
        }
    }

    /** Required managed product version is unavailable. */
    public static final class MissingDependency extends BlockedReason {
        private final ProductId product;
        private final ProductId dependency;
        private final SemanticVersion minimumVersion;

        /**
         * Creates the reason.
         *
         * @param product product declaring the dependency
         * @param dependency missing managed product
         * @param minimumVersion oldest acceptable dependency version
         */
        public MissingDependency(@NotNull ProductId product, @NotNull ProductId dependency,
                                 @NotNull SemanticVersion minimumVersion) {
            this.product = product;
            this.dependency = dependency;
            this.minimumVersion = minimumVersion;
        }

        /** Product declaring the dependency. */
        @NotNull
        public ProductId getProduct() {
            return product;
        }

        /** Missing managed product. */
        @NotNull
        public ProductId getDependency() {
            return dependency;
        }

        /** Oldest acceptable dependency version. */
        @NotNull
        public SemanticVersion getMinimumVersion() {
            return minimumVersion;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MissingDependency)) {
                return false;
            }
            MissingDependency that = (MissingDependency) other;
            return product.equals(that.product) && dependency.equals(that.dependency)
                && minimumVersion.equals(that.minimumVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(product, dependency, minimumVersion);
        }

        @Override
        public String toString() {
            return "MissingDependency(product=" + product + ", dependency=" + dependency
                + ", minimumVersion=" + minimumVersion + ")";
        }
    }

    /** Required third-party plugin is absent or too old. */
    public static final class MissingExternalPluginDependency extends BlockedReason {
        private final ProductId product;
        private final String plugin;
        private final SemanticVersion minimumVersion;
        private final String downloadPage;

        /**
         * Creates the reason.
         *
         * @param product product declaring the dependency
         * @param plugin third-party plugin name
         * @param minimumVersion oldest acceptable plugin version
         * @param downloadPage optional administrator-facing download page
         */
        public MissingExternalPluginDependency(@NotNull ProductId product, @NotNull String plugin,
                                               @NotNull SemanticVersion minimumVersion, @Nullable String downloadPage) {
            this.product = product;
            this.plugin = plugin;
            this.minimumVersion = minimumVersion;
            this.downloadPage = downloadPage;
        }

        /** Product declaring the dependency. */
        @NotNull
        public ProductId getProduct() {
            return product;
        }

        /** Third-party plugin name. */
        @NotNull
        public String getPlugin() {
            return plugin;
        }

        /** Oldest acceptable plugin version. */
        @NotNull
        public SemanticVersion getMinimumVersion() {
            return minimumVersion;
        }

        /** Administrator-facing download page, when declared. */
        @Nullable
        public String getDownloadPage() {
            return downloadPage;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MissingExternalPluginDependency)) {
                return false;
            }
            MissingExternalPluginDependency that = (MissingExternalPluginDependency) other;
            return product.equals(that.product) && plugin.equals(that.plugin)
                && minimumVersion.equals(that.minimumVersion) && Objects.equals(downloadPage, that.downloadPage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(product, plugin, minimumVersion, downloadPage);
        }

        @Override
        public String toString() {
            return "MissingExternalPluginDependency(product=" + product + ", plugin=" + plugin
                + ", minimumVersion=" + minimumVersion + ", downloadPage=" + downloadPage + ")";
        }
    }

    /** Product updates are explicitly frozen. */
    public static final class Frozen extends BlockedReason {
        private final ProductId product;

        /** Creates the reason for the frozen managed {@code product}. */
        public Frozen(@NotNull ProductId product) {
            this.product = product;
        }

        /** Frozen managed product. */
        @NotNull
        public ProductId getProduct() {
            return product;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Frozen && product.equals(((Frozen) other).product);
        }

        @Override
        public int hashCode() {
            return product.hashCode();
        }

        @Override
        public String toString() {
            return "Frozen(product=" + product + ")";
        }
    }

    /** No published release satisfies the required API generation. */
    public static final class NoCompatibleRelease extends BlockedReason {
        private final ProductId product;
        private final int requiredApi;

        /**
         * Creates the reason.
         *
         * @param product product without a compatible release
         * @param requiredApi API generation required by the candidate graph
         */
        public NoCompatibleRelease(@NotNull ProductId product, int requiredApi) {
            this.product = product;
            this.requiredApi = requiredApi;
        }

        /** Product without a compatible release. */
        @NotNull
        public ProductId getProduct() {
            return product;
        }

        /** API generation required by the candidate graph. */
        public int getRequiredApi() {
            return requiredApi;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof NoCompatibleRelease)) {
                return false;
            }
            NoCompatibleRelease that = (NoCompatibleRelease) other;
            return requiredApi == that.requiredApi && product.equals(that.product);
        }

        @Override
        public int hashCode() {
            return Objects.hash(product, requiredApi);
        }

        @Override
        public String toString() {
            return "NoCompatibleRelease(product=" + product + ", requiredApi=" + requiredApi + ")";
        }
    }
}
