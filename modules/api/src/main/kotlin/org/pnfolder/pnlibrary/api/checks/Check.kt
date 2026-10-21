package org.pnfolder.pnlibrary.api.checks

import org.pnfolder.pnlibrary.api.checks.internal.CheckSerializer
import org.pnfolder.pnlibrary.api.config.ConfigSerializeWith

/**
 * A question about a player or a value: "yes" or "no". Checks never change anything.
 *
 * In configuration a check is an object with a `type` and the fields of that type, such as
 * `type: permission` with `permission: shop.use`. A text names a [Requirement] declared under `checks:`.
 * Implementations should be free of side effects, because one check may run many times.
 */
@ConfigSerializeWith(CheckSerializer::class)
fun interface Check {
    /** Returns whether [context] satisfies this check. */
    fun matches(context: CheckContext): Boolean
}
