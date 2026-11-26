package org.pnfolder.pnlibrary.updates.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;

/** Thread-safe ownership boundary for update registrations. */
public final class UpdateRegistrationStore {
    private final CopyOnWriteArrayList<RegisteredUpdate> entries = new CopyOnWriteArrayList<>();

    /** Adds a registration. */
    public void add(@NotNull RegisteredUpdate entry) {
        entries.add(entry);
    }

    /** Removes a registration. */
    public void remove(@NotNull RegisteredUpdate entry) {
        entries.remove(entry);
    }

    /** Copy of the registrations for internal use. */
    @NotNull
    public List<RegisteredUpdate> snapshot() {
        return new ArrayList<>(entries);
    }

    /** Read-only copy exposed to plugins. */
    @NotNull
    public List<UpdateRegistration> publicSnapshot() {
        return Collections.<UpdateRegistration>unmodifiableList(new ArrayList<UpdateRegistration>(entries));
    }

    /** Finds a registration by product id or repository name, ignoring case. */
    @Nullable
    public RegisteredUpdate find(@NotNull String product) {
        for (RegisteredUpdate entry : entries) {
            if (entry.getProduct().equalsIgnoreCase(product) || entry.getRequest().getRepositoryName().equalsIgnoreCase(product)) {
                return entry;
            }
        }
        return null;
    }

    /** Marks all registrations closed and clears the store. */
    public void close() {
        for (RegisteredUpdate entry : entries) {
            entry.markClosed();
        }
        entries.clear();
    }
}
