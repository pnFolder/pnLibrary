package org.pnfolder.pnlibrary.api.chat

/**
 * One short message of a command, rendered by [ChatKit.notice].
 *
 * @property tone visual weight of the message, such as success or failure
 * @property text the main line of the message
 * @property detail an optional second line, such as the reason of a failure
 */
data class ChatNotice(val tone: ChatTone, val text: String, val detail: String? = null)
