package ru.privatenull.pnlibrary.core.updates.engine

import java.util.UUID

/** A preparation must not be health-checked as if a restart already happened in this process. */
internal object UpdateProcessSession { val id: String = UUID.randomUUID().toString() }
