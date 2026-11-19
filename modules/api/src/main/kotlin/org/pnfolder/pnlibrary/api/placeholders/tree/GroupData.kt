package org.pnfolder.pnlibrary.api.placeholders.tree


/** The settings and branches of one `placeholders { }` block. */
internal class GroupData(val papiPrefix: String?) {
    val roots = mutableListOf<Node>()
    var format: FormatChoice = FormatChoice.Inherit
    var access: AccessRule = AccessRule.AllowAll
}
