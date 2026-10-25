package org.pnfolder.pnlibrary.api.config

import java.io.File

/**
 * Result of loading and synchronizing one code-first configuration file.
 *
 * All path collections use full dot-separated YAML paths and are empty when that synchronization
 * phase made no changes.
 *
 * @property value validated typed value now held by the managed configuration
 * @property addedPaths missing default values inserted into the document
 * @property backup backup of the original file, or `null` when no backup was needed or enabled
 * @property removedPaths unknown YAML values removed according to [UnknownValuePolicy.REMOVE]
 * @property addedComments code-defined comments inserted into the document
 * @property appliedMigrations ordered migration labels applied before decoding
 */
data class ConfigLoadResult<T>(
    val value: T,
    val addedPaths: List<String>,
    val backup: File?,
    val removedPaths: List<String> = emptyList(),
    val addedComments: List<String> = emptyList(),
    val appliedMigrations: List<String> = emptyList(),
)
