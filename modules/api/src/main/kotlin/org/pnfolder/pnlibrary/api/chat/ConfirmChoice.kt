package org.pnfolder.pnlibrary.api.chat

/**
 * One answer on a confirmation card that offers several, such as restarting now or later.
 *
 * @property label text of the button
 * @property command the command the button runs; a reader that cannot click is told to type it
 * @property hint what the button does, shown on hover
 */
data class ConfirmChoice(val label: String, val command: String, val hint: String)
