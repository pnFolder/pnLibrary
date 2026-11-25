package org.pnfolder.pnlibrary.api.placeholders.annotation

import org.pnfolder.pnlibrary.api.placeholders.route.RouteInfo

/**
 * Marks the parameter that receives the [RouteInfo]: every part of the asked address and what each one is
 * (group, branch, argument, field, format). `fun war(@Route route: RouteInfo): WarBranch`
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Route
