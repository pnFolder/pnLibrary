package org.pnfolder.pnlibrary.core.updates.service

import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

/** Thread-safe ownership boundary for update registrations. */
internal class UpdateRegistrationStore {
    private val entries = CopyOnWriteArrayList<RegisteredUpdate>()

    /** Adds a registration. */
    fun add(entry: RegisteredUpdate) { entries += entry }
    /** Removes a registration. */
    fun remove(entry: RegisteredUpdate) { entries.remove(entry) }
    /** Copy of the registrations for internal use. */
    fun snapshot(): List<RegisteredUpdate> = entries.toList()
    /** Read-only copy exposed to plugins. */
    fun publicSnapshot(): List<UpdateRegistration> = Collections.unmodifiableList(ArrayList(entries))
    /** Finds a registration by product id or repository name, ignoring case. */
    fun find(product: String): RegisteredUpdate? = entries.firstOrNull {
        it.product.equals(product, true) || it.request.repositoryName.equals(product, true)
    }
    /** Marks all registrations closed and clears the store. */
    fun close() {
        entries.forEach(RegisteredUpdate::markClosed)
        entries.clear()
    }
}
