package org.pnfolder.pnlibrary.api.placeholders.reply

/** Used inside the library to end an address with an answer; plugins use [Reply.stop]. */
class PlaceholderAnswer(val value: Any?) : RuntimeException(null, null, false, false)
