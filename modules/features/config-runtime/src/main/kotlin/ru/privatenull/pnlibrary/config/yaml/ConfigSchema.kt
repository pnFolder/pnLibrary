package ru.privatenull.pnlibrary.config.yaml

/** Optional schema metadata understood by the managed YAML lifecycle. */
internal interface ConfigSchema {
    val requiredPaths: Set<String>
}
