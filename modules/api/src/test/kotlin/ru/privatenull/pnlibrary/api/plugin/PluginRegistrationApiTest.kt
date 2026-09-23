package ru.privatenull.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PluginRegistrationApiTest {
    @Test
    fun `registry exposes only physical plugin registration`() {
        val declared = PluginRegistry::class.java.declaredMethods.toList()
        val registrations = declared.filter { it.name == "register" }

        assertEquals(1, registrations.size)
        assertEquals(PluginRegistration::class.java, registrations.single().returnType)
        assertEquals(listOf(Any::class.java), registrations.single().parameterTypes.toList())
        assertFalse(declared.any { it.name == "modules" || it.name == "unregisterOwner" })
    }

    @Test
    fun `plugin registration owns logical modules but not module capabilities`() {
        val methods = PluginRegistration::class.java.methods.map { it.name }.toSet()

        assertTrue("registerModule" in methods)
        assertTrue("getModule" in methods)
        assertTrue("unregisterModule" in methods)
        assertTrue("modules" in methods)
        assertFalse("getEvents" in methods)
        assertFalse("getTasks" in methods)
    }
}
