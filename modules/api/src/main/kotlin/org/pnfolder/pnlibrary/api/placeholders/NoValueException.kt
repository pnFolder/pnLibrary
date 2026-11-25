package org.pnfolder.pnlibrary.api.placeholders

/** Thrown inside a placeholder method when it has no value to give; the placeholder is then left unresolved. */
class NoValueException(message: String = "No value") : RuntimeException(message, null, false, false)
