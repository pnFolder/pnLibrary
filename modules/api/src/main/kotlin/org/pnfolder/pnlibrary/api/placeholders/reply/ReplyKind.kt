package org.pnfolder.pnlibrary.api.placeholders.reply

import org.pnfolder.pnlibrary.api.placeholders.annotation.Placeholder

/** What a [Reply] holds. */
enum class ReplyKind {
    /** An answer: the address ends here with this value. */
    VALUE,

    /** An object that continues the address: the next words are its `@Placeholder` members. */
    NEXT,

    /** An answer given from a branch: the address ends here and nothing below is called. */
    STOP,

    /** No value: the address stays as it was written. */
    NONE,
}
