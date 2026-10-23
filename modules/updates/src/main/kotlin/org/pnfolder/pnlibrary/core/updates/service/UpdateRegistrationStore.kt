package org.pnfolder.pnlibrary.core.updates.service

import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/** Thread-safe ownership boundary for update registrations. */
internal class UpdateRegistrationStore {
    private val entries = CopyOnWriteArrayList<RegisteredUpdate>()

    fun add(entry: RegisteredUpdate) { entries += entry }
    fun remove(entry: RegisteredUpdate) { entries.remove(entry) }
    fun snapshot(): List<RegisteredUpdate> = entries.toList()
    fun publicSnapshot(): List<UpdateRegistration> = Collections.unmodifiableList(ArrayList(entries))
    fun find(product: String): RegisteredUpdate? = entries.firstOrNull {
        it.product.equals(product, true) || it.request.repositoryName.equals(product, true)
    }
    fun close() {
        entries.forEach(RegisteredUpdate::markClosed)
        entries.clear()
    }
}
