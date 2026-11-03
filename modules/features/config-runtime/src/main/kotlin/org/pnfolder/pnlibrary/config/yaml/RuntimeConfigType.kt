package org.pnfolder.pnlibrary.config.yaml

/**
 * Runtime extension of a polymorphic configuration type registry.
 *
 * @property owner plugin namespace that registered the implementation
 * @property baseType annotated polymorphic contract
 * @property implementation concrete no-argument implementation
 * @property name canonical discriminator value
 * @property aliases additional accepted discriminator values
 * @property priority tie-breaker when compatible declarations overlap
 */
internal data class RuntimeConfigType(
    val owner: String,
    val baseType: Class<*>,
    val implementation: Class<*>,
    val name: String,
    val aliases: Set<String>,
    val priority: Int,
)
