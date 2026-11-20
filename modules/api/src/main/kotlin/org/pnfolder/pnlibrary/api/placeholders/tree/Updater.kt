package org.pnfolder.pnlibrary.api.placeholders.tree

/** The way to change a placeholder from outside, and who may do it. */
internal class Updater(val rule: AccessRule, val run: (PlaceholderCall<Any?>, String) -> Any?)
