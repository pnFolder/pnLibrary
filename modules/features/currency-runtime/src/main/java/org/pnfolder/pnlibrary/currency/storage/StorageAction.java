package org.pnfolder.pnlibrary.currency.storage;

import java.io.IOException;

/** A read or write of the currency file that runs on the storage worker. */
interface StorageAction<T> {
    T run() throws IOException;
}
