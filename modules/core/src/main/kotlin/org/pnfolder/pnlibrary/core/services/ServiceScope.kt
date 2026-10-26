package org.pnfolder.pnlibrary.core.services

import org.pnfolder.pnlibrary.api.services.ServiceManager

/** Module view whose registrations are removed together when the module closes. */
internal interface ServiceScope : ServiceManager, AutoCloseable
