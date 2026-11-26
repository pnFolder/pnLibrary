package org.pnfolder.pnlibrary.api.placeholders.reply;

/** What a {@link Reply} holds. */
public enum ReplyKind {
    /** An answer: the address ends here with this value. */
    VALUE,
    /** An object that continues the address: the next words are its {@code @Placeholder} members. */
    NEXT,
    /** An answer given from a branch: the address ends here and nothing below is called. */
    STOP,
    /** No value: the address stays as it was written. */
    NONE
}
