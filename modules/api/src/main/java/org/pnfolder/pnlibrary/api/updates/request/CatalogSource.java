package org.pnfolder.pnlibrary.api.updates.request;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Where a plugin's release catalog ({@code releases.json}) lives on GitHub: repository, branch (or tag or commit) and
 * folder.
 *
 * <p>The defaults are branch {@code main} and folder {@code .pnlibrary}. Plugins normally do not build one directly;
 * {@link PluginUpdateRequest.Builder#branch} and {@link PluginUpdateRequest.Builder#catalogPath} fill it in.
 */
public final class CatalogSource {
    /** Branch read when the plugin names none. */
    public static final String DEFAULT_BRANCH = "main";
    /** Catalog folder used when the plugin names none. */
    public static final String DEFAULT_PATH = ".pnlibrary";

    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_./-]+");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-fA-F]{7,40}");
    private static final String FILE = "releases.json";

    private final String owner;
    private final String repository;
    private final String branch;
    private final String path;
    private final RefType refType;

    /** Creates a source on the default branch and folder. */
    public CatalogSource(@NotNull String owner, @NotNull String repository) {
        this(owner, repository, DEFAULT_BRANCH);
    }

    /** Creates a source on {@code branch} and the default folder. */
    public CatalogSource(@NotNull String owner, @NotNull String repository, @NotNull String branch) {
        this(owner, repository, branch, DEFAULT_PATH);
    }

    /** Creates a source on {@code branch} and {@code path}. */
    public CatalogSource(@NotNull String owner, @NotNull String repository, @NotNull String branch,
                         @NotNull String path) {
        this(owner, repository, branch, path, RefType.BRANCH);
    }

    /**
     * Creates a source.
     *
     * @param owner GitHub repository owner
     * @param repository GitHub repository name
     * @param branch branch, tag or commit that holds the catalog, as {@code refType} says
     * @param path folder of the catalog inside the repository, without leading or trailing slashes
     * @param refType what {@code branch} names; a branch unless the plugin pins a tag or a commit
     */
    public CatalogSource(@NotNull String owner, @NotNull String repository, @NotNull String branch,
                         @NotNull String path, @NotNull RefType refType) {
        if (!REPOSITORY_PART.matcher(owner).matches() || !REPOSITORY_PART.matcher(repository).matches()) {
            throw new IllegalArgumentException("Invalid GitHub repository: " + owner + "/" + repository);
        }
        if (!isSafeName(branch)) {
            throw new IllegalArgumentException("Invalid branch name: " + branch);
        }
        if (refType == RefType.COMMIT && !COMMIT.matcher(branch).matches()) {
            throw new IllegalArgumentException("Invalid commit hash: " + branch);
        }
        if (!isSafeName(path)) {
            throw new IllegalArgumentException("Invalid catalog path: " + path);
        }
        this.owner = owner;
        this.repository = repository;
        this.branch = branch;
        this.path = path;
        this.refType = refType;
    }

    private static boolean isSafeName(String value) {
        return NAME.matcher(value).matches() && !value.contains("..") && !value.startsWith("/")
            && !value.endsWith("/");
    }

    /** GitHub repository owner. */
    @NotNull
    public String getOwner() {
        return owner;
    }

    /** GitHub repository name. */
    @NotNull
    public String getRepository() {
        return repository;
    }

    /** Branch, tag or commit that holds the catalog, as {@link #getRefType()} says. */
    @NotNull
    public String getBranch() {
        return branch;
    }

    /** Folder of the catalog inside the repository, without leading or trailing slashes. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** What {@link #getBranch()} names; a branch unless the plugin pins a tag or a commit. */
    @NotNull
    public RefType getRefType() {
        return refType;
    }

    /** Address of {@code releases.json} on raw.githubusercontent.com. */
    @NotNull
    public String getRawUrl() {
        return "https://raw.githubusercontent.com/" + owner + "/" + repository + "/" + refType.rawPrefix + branch
            + "/" + path + "/" + FILE;
    }

    /** Address of {@code releases.json} in the GitHub Contents API. */
    @NotNull
    public String getApiUrl() {
        return "https://api.github.com/repos/" + owner + "/" + repository + "/contents/" + path + "/" + FILE
            + "?ref=" + branch;
    }

    /** Address of {@code releases.json} on jsDelivr. */
    @NotNull
    public String getCdnUrl() {
        return "https://cdn.jsdelivr.net/gh/" + owner + "/" + repository + "@" + branch + "/" + path + "/" + FILE;
    }

    /** Short human-readable form, for example {@code PnFolder/pnCases@master:updates}. */
    @Override
    public String toString() {
        return owner + "/" + repository + "@" + branch + ":" + path;
    }

    /** Two sources are equal when owner, repository, branch, path and reference type all match. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CatalogSource)) {
            return false;
        }
        CatalogSource that = (CatalogSource) other;
        return owner.equals(that.owner) && repository.equals(that.repository) && branch.equals(that.branch)
            && path.equals(that.path) && refType == that.refType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, repository, branch, path, refType);
    }

    /** What a {@link CatalogSource} reference names. */
    public enum RefType {
        /** A branch; the catalog follows its newest commit. */
        BRANCH("refs/heads/"),

        /** A tag; the catalog stays as it was when the tag was made. */
        TAG("refs/tags/"),

        /** A commit hash of 7 to 40 hexadecimal characters. */
        COMMIT("");

        private final String rawPrefix;

        RefType(String rawPrefix) {
            this.rawPrefix = rawPrefix;
        }
    }
}
