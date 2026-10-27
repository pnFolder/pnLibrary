package org.pnfolder.pnlibrary.api.updates

/**
 * Where a plugin's release catalog (`releases.json`) lives on GitHub: repository, branch and folder.
 *
 * The defaults are branch `main` and folder `.pnlibrary`. Plugins normally do not build one directly;
 * [PluginUpdateRequest.Builder.branch] and [PluginUpdateRequest.Builder.catalogPath] fill it in.
 */
class CatalogSource @JvmOverloads constructor(
    /** GitHub repository owner. */
    val owner: String,
    /** GitHub repository name. */
    val repository: String,
    /** Branch that holds the catalog. */
    val branch: String = DEFAULT_BRANCH,
    /** Folder of the catalog inside the repository, without leading or trailing slashes. */
    val path: String = DEFAULT_PATH,
) {
    init {
        require(owner.matches(REPOSITORY_PART) && repository.matches(REPOSITORY_PART)) {
            "Invalid GitHub repository: $owner/$repository"
        }
        require(branch.matches(NAME) && !branch.contains("..") && !branch.startsWith("/") && !branch.endsWith("/")) {
            "Invalid branch name: $branch"
        }
        require(path.matches(NAME) && !path.contains("..") && !path.startsWith("/") && !path.endsWith("/")) {
            "Invalid catalog path: $path"
        }
    }

    /** Address of `releases.json` on raw.githubusercontent.com. */
    val rawUrl: String
        get() = "https://raw.githubusercontent.com/$owner/$repository/refs/heads/$branch/$path/$FILE"

    /** Address of `releases.json` in the GitHub Contents API. */
    val apiUrl: String
        get() = "https://api.github.com/repos/$owner/$repository/contents/$path/$FILE?ref=$branch"

    /** Address of `releases.json` on jsDelivr. */
    val cdnUrl: String
        get() = "https://cdn.jsdelivr.net/gh/$owner/$repository@$branch/$path/$FILE"

    /** Short human-readable form, for example `PnFolder/pnCases@master:updates`. */
    override fun toString(): String = "$owner/$repository@$branch:$path"

    override fun equals(other: Any?): Boolean =
        other is CatalogSource && owner == other.owner && repository == other.repository &&
            branch == other.branch && path == other.path

    override fun hashCode(): Int = arrayOf(owner, repository, branch, path).contentHashCode()

    companion object {
        private val REPOSITORY_PART = Regex("[A-Za-z0-9_.-]+")
        private val NAME = Regex("[A-Za-z0-9_./-]+")
        private const val FILE = "releases.json"

        /** Branch read when the plugin names none. */
        const val DEFAULT_BRANCH = "main"

        /** Catalog folder used when the plugin names none. */
        const val DEFAULT_PATH = ".pnlibrary"
    }
}
