package org.pnfolder.pnlibrary.core.remote

import org.pnfolder.pnlibrary.console.ReportedFailure

/** The startup policy stopped the plugin; its console card has already been printed. */
class PolicyDeniedException(reason: String) : IllegalStateException("Startup stopped by policy: $reason"), ReportedFailure
