package org.pnfolder.pnlibrary.api.updates

/**
 * Where a plugin's release catalog (`releases.json`) lives on GitHub: repository, branch (or tag or commit) and folder.
 *
 * The defaults are branch `main` and folder `.pnlibrary`. Plugins normally do not build one directly;
 * [PluginUpdateRequest.Builder.branch] and [PluginUpdateRequest.Builder.catalogPath] fill it in.
 */
class CatalogSource @JvmOverloads constructor(
    /** GitHub repository owner. */
    val owner: String,
    /** GitHub repository name. */
    val repository: String,
    /** Branch, tag or commit that holds the catalog, as [refType] says. */
    val branch: String = DEFAULT_BRANCH,
    /** Folder of the catalog inside the repository, without leading or trailing slashes. */
    val path: String = DEFAULT_PATH,
    /** What [branch] names; a branch unless the plugin pins a tag or a commit. */
    val refType: RefType = RefType.BRANCH,
) {
    init {
        require(owner.matches(REPOSITORY_PART) && repository.matches(REPOSITORY_PART)) {
            "Invalid GitHub repository: $owner/$repository"
        }
        require(branch.matches(NAME) && !branch.contains("..") && !branch.startsWith("/") && !branch.endsWith("/")) {
            "Invalid branch name: $branch"
        }
        require(refType != RefType.COMMIT || branch.matches(COMMIT)) { "Invalid commit hash: $branch" }
        require(path.matches(NAME) && !path.contains("..") && !path.startsWith("/") && !path.endsWith("/")) {
            "Invalid catalog path: $path"
        }
    }

    /** Address of `releases.json` on raw.githubusercontent.com. */
    val rawUrl: String
        get() = "https://raw.githubusercontent.com/$owner/$repository/${refType.rawPrefix}$branch/$path/$FILE"

    /** Address of `releases.json` in the GitHub Contents API. */
    val apiUrl: String
        get() = "https://api.github.com/repos/$owner/$repository/contents/$path/$FILE?ref=$branch"

    /** Address of `releases.json` on jsDelivr. */
    val cdnUrl: String
        get() = "https://cdn.jsdelivr.net/gh/$owner/$repository@$branch/$path/$FILE"

    /** What a [CatalogSource] reference names. */
    enum class RefType(internal val rawPrefix: String) {
        /** A branch; the catalog follows its newest commit. */
        BRANCH("refs/heads/"),

        /** A tag; the catalog stays as it was when the tag was made. */
        TAG("refs/tags/"),

        /** A commit hash of 7 to 40 hexadecimal characters. */
        COMMIT(""),
    }

    /** Short human-readable form, for example `PnFolder/pnCases@master:updates`. */
    override fun toString(): String = "$owner/$repository@$branch:$path"

    /** Two sources are equal when owner, repository, branch and path all match. */
    override fun equals(other: Any?): Boolean =
        other is CatalogSource && owner == other.owner && repository == other.repository &&
            branch == other.branch && path == other.path && refType == other.refType

    /** Hash consistent with [equals]. */
    override fun hashCode(): Int = arrayOf(owner, repository, branch, path, refType).contentHashCode()

    /** Constants and factories for [CatalogSource]. */
    companion object {
        private val REPOSITORY_PART = Regex("[A-Za-z0-9_.-]+")
        private val NAME = Regex("[A-Za-z0-9_./-]+")
        private val COMMIT = Regex("[0-9a-fA-F]{7,40}")
        private const val FILE = "releases.json"

        /** Branch read when the plugin names none. */
        const val DEFAULT_BRANCH = "main"

        /** Catalog folder used when the plugin names none. */
        const val DEFAULT_PATH = ".pnlibrary"
    }
}
