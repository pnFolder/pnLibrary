package org.pnfolder.pnlibrary.api.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemotePolicyResultTest {
    @Test
    fun `plain decisions carry no custom card`() {
        val denied = RemotePolicyResult.deny("Версия устарела")

        assertFalse(denied.allowed)
        assertEquals("Версия устарела", denied.message)
        assertNull(denied.status)
        assertNull(denied.headline)
        assertTrue(denied.details.isEmpty() && denied.steps.isEmpty() && denied.links.isEmpty())
        assertTrue(RemotePolicyResult.allow().allowed)
    }

    @Test
    fun `a built decision keeps its card in the order it was written`() {
        val result = RemotePolicyResult.denying("Версия устарела")
            .status("устарела")
            .headline("нужно обновление")
            .reason("Минимум 0.0.2")
            .reason("Группа") { it.child("вложенная причина") }
            .detail("Установлена", "0.0.1")
            .detail("Минимальная", "0.0.2")
            .step("Скачайте новую версию")
            .step("Перезапустите сервер")
            .link("Скачать", "https://example.org/download")
            .build()

        assertFalse(result.allowed)
        assertEquals("устарела", result.status)
        assertEquals("нужно обновление", result.headline)
        assertEquals(listOf("Установлена", "Минимальная"), result.details.keys.toList())
        assertEquals(listOf("Скачайте новую версию", "Перезапустите сервер"), result.steps)
        assertEquals("https://example.org/download", result.links["Скачать"])
        assertEquals(listOf("Минимум 0.0.2", "Группа"), result.explanation.children.map { it.text })
    }

    @Test
    fun `blank values are rejected or dropped`() {
        val builder = RemotePolicyResult.allowing("ok").status("   ").headline("")
        assertNull(builder.build().status)
        assertNull(builder.build().headline)
        assertThrows(IllegalArgumentException::class.java) { RemotePolicyResult.denying("x").step(" ") }
        assertThrows(IllegalArgumentException::class.java) { RemotePolicyResult.denying("x").link("", "https://a.b") }
        assertThrows(IllegalArgumentException::class.java) { RemotePolicyResult.denying("x").detail(" ", "v") }
    }
}
