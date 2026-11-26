package org.pnfolder.pnlibrary.api.commands.definition;

/** Structural role of a node in a command tree. */
public enum CommandNodeKind {
    /** The command's own label. */
    ROOT,
    /** A fixed word. */
    LITERAL,
    /** A typed value. */
    ARGUMENT
}
