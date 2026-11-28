package org.pnfolder.pnlibrary.kotlin

import java.util.concurrent.CompletionStage
import org.pnfolder.pnlibrary.api.network.NetworkServer
import org.pnfolder.pnlibrary.api.network.channel.NetworkChannel
import org.pnfolder.pnlibrary.api.network.channel.ServerChannel
import org.pnfolder.pnlibrary.api.network.message.MessageWriter

/**
 * Sends a message of [type] to [server]; the writer is `this`:
 * `channel.sendMessage(server, "balance") { writeUuid(id).writeInt(250) }`.
 */
public fun NetworkChannel.sendMessage(server: NetworkServer, type: String, body: MessageWriter.() -> Unit): Boolean =
    send(server, type) { it.body() }

/** Sends a message of [type] to every server that can take it and returns how many did. */
public fun NetworkChannel.broadcastMessage(type: String, body: MessageWriter.() -> Unit): Int =
    broadcast(type) { it.body() }

/** Sends a message of [type] to the proxy; the writer is `this`. */
public fun ServerChannel.sendMessage(type: String, body: MessageWriter.() -> Unit): CompletionStage<Boolean> =
    send(type) { it.body() }
