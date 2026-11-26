package org.pnfolder.pnlibrary.api.audience;

import net.kyori.adventure.audience.Audience;

/**
 * The server console: an audience that is not a player. It has a name and grants nothing it was not given.
 *
 * <pre>{@code
 * library.getAudiences().console().sendMessage(Component.text("Started"));
 * }</pre>
 */
public interface Console extends Audience {
}
