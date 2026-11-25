package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.info.FormatInfo
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo
import org.pnfolder.pnlibrary.api.registry.RegistryEntry

internal fun PlaceholderInfo.asEntry() = RegistryEntry(address, this, owner, visibility, description)

internal fun FormatDef.asEntry() = RegistryEntry(name, FormatInfo(name, input.simpleName), owner.ifEmpty { "pnLibrary" })
