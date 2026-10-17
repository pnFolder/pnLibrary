package ru.privatenull.pnlibrary.api.updates

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.PluginDependency

class UpdateApiConsistencyTest {
    @Test
    fun `release history is an immutable snapshot of caller data`() {
        val release = ReleaseSummary("2.0.0", UpdateChannel.STABLE)
        val source = mutableListOf(release)
        val snapshot = UpdateSnapshot(
            "product", "1.0.0", "2.0.0", UpdateChannel.STABLE, UpdateState.UPDATE_AVAILABLE,
            17, 17, false, null, null, availableReleases = source,
        )
        source.clear()
        assertEquals(listOf(release), snapshot.availableReleases)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.availableReleases as MutableList<ReleaseSummary>).clear()
        }
    }

    @Test
    fun `registry vocabulary returns immutable snapshots`() {
        val registration = object : UpdateRegistration {
            override val repository = "owner/repository"
            override val snapshot = UpdateSnapshot(
                "product", "1.0.0", null, UpdateChannel.STABLE, UpdateState.UP_TO_DATE,
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

            override fun all(): List<UpdateRegistration> = java.util.Collections.unmodifiableList(listOf(registration))
            override fun get(product: String): UpdateRegistration? =
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
