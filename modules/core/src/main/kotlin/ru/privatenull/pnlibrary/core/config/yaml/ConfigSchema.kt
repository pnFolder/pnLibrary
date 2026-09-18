package ru.privatenull.pnlibrary.core.config.yaml

/** Optional schema metadata understood by the managed YAML lifecycle. */
internal interface ConfigSchema {
    val requiredPaths: Set<String>
}
