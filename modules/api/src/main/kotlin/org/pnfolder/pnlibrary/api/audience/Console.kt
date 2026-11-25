package org.pnfolder.pnlibrary.api.audience

import net.kyori.adventure.audience.Audience

/**
 * The server console: an audience that is not a player. It has a name and grants nothing it was not given.
 *
 * ```kotlin
 * library.audiences.console().sendMessage(Component.text("Started"))
 * ```
 */
interface Console : Audience
