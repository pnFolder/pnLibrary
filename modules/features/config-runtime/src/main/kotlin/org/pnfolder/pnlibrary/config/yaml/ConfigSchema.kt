package org.pnfolder.pnlibrary.config.yaml

/** Optional schema metadata understood by the managed YAML lifecycle. */
internal interface ConfigSchema {
    /** Dotted paths of the fields marked `@ConfigRequired`; they must be present in an existing file. */
    val requiredPaths: Set<String>
}
