package org.pnfolder.pnlibrary.kotlin

import org.pnfolder.pnlibrary.api.network.message.MessageCodec
import org.pnfolder.pnlibrary.api.network.message.MessageReader
import org.pnfolder.pnlibrary.api.network.message.MessageWriter

/**
 * A [MessageCodec] from two lambdas; the writer and the reader are the lambdas' receivers, so no `out.` and `in.`:
 *
 * ```
 * val balance = messageCodec<Balance>(
 *     encode = { b -> writeUuid(b.id).writeVarInt(b.coins) },
 *     decode = { Balance(readUuid(), readVarInt()) },   // same order as encode
 * )
 * ```
 */
public fun <T : Any> messageCodec(
    encode: MessageWriter.(T) -> Unit,
    decode: MessageReader.() -> T,
): MessageCodec<T> = object : MessageCodec<T> {
    override fun encode(message: T, out: MessageWriter) = out.encode(message)

    override fun decode(input: MessageReader): T = input.decode()
}
