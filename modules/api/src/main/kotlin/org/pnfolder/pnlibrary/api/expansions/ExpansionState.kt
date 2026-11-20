package org.pnfolder.pnlibrary.api.expansions

/** Lifecycle of an [Expansion]. [CLOSED] is terminal; a [FAILED] expansion may be started again with [Expansion.enable]. */
enum class ExpansionState { DISABLED, ENABLED, FAILED, CLOSED }
