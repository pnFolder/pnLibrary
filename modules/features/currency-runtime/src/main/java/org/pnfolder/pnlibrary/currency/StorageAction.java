package org.pnfolder.pnlibrary.currency;

import java.io.IOException;

/** A read or write of the currency file that runs on the storage worker. */
interface StorageAction<T> {
    T run() throws IOException;
}
