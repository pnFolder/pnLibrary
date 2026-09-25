package ru.privatenull.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.PluginDependency

class UpdateApiConsistencyTest {
    @Test
    fun `registry vocabulary delegates to compatibility implementation and returns immutable snapshots`() {
        val registration = object : UpdateRegistration {
            override val repository = "owner/repository"
            override val snapshot = UpdateSnapshot(
                "product", "1.0.0", null, UpdateChannel.STABLE, UpdateState.CURRENT,
                17, 17, false, null, null,
            )
            override fun checkNow() = Unit
            override fun downloadNow() = Unit
            override fun close() = Unit
        }
        val service = object : UpdateService {
            override fun register(
                owner: Any,
                product: ProductDescriptor,
                request: PluginUpdateRequest,
                dependencies: List<PluginDependency>,
            ) = registration

            override fun registrations(): List<UpdateRegistration> = mutableListOf(registration)
            override fun find(product: String): UpdateRegistration? =
                registration.takeIf { product.equals("product", true) }
        }

        assertSame(registration, service.get("PRODUCT"))
        assertSame(registration, service.require("product"))
        assertThrows(IllegalStateException::class.java) { service.require("missing") }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (service.all() as MutableList<UpdateRegistration>).clear()
        }
    }
}
