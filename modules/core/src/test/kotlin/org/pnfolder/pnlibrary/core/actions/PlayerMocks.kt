package org.pnfolder.pnlibrary.core.actions

import org.mockito.Answers
import org.mockito.Mockito
import org.pnfolder.pnlibrary.api.audience.LibraryPlayer
import org.pnfolder.pnlibrary.api.audience.senderPointers
import java.util.UUID

/** A player mock that keeps Adventure's default `get(Pointer)`, so `name` and `hasPermission` read [senderPointers] like in production. */
internal fun mockPlayer(name: String = "Alice", id: UUID = UUID.randomUUID(), extra: Class<*>? = null, permission: (String) -> Boolean = { false }): LibraryPlayer {
    val settings = Mockito.withSettings().defaultAnswer { call ->
        if (call.method.isDefault) call.callRealMethod() else Answers.RETURNS_DEFAULTS.answer(call)
    }
    val player = Mockito.mock(LibraryPlayer::class.java, if (extra == null) settings else settings.extraInterfaces(extra))
    Mockito.doReturn(id).`when`(player).uniqueId
    Mockito.doReturn(senderPointers(name, id, permission)).`when`(player).pointers()
    return player
}
