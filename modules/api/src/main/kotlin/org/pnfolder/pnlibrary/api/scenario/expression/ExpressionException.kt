package org.pnfolder.pnlibrary.api.scenario.expression


/** A mistake in the text of an expression, or a value an expression cannot work with. */
class ExpressionException(message: String) : IllegalArgumentException(message)
