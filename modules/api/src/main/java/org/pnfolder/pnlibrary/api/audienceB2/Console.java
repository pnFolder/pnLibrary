package org.pnfolder.pnlibrary.api.audienceB2;

import net.kyori.adventure.audience.Audience;

/**
 * The server console: an audience that is not a player.
 *
 * <pre>{@code
 * library.audiences().console().sendMessage(Component.text("Started"));
 * }</pre>
 */
public interface Console extends Audience {
}
