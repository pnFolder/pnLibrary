package org.pnfolder.pnlibrary.api.remote

/**
 * Identity of the product being evaluated.
 *
 * @property id stable machine-readable product identifier
 * @property name human-readable product name
 * @property version installed product version
 */
data class ProductInfo(val id: String, val name: String, val version: String)
