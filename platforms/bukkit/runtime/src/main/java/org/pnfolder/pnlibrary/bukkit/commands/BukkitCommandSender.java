package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.function.BooleanSupplier;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

/** The portable view of a native Bukkit sender: replies go to its audience, the native sender stays reachable. */
public final class BukkitCommandSender implements CommandSender, ForwardingAudience.Single {
    private final org.bukkit.command.CommandSender nativeSender;
    private final Audience audience;
    private final BooleanSupplier transportAvailable;

    public BukkitCommandSender(org.bukkit.command.CommandSender nativeSender, Audience audience) {
        this(nativeSender, audience, () -> true);
    }

    public BukkitCommandSender(org.bukkit.command.CommandSender nativeSender, Audience audience,
                               BooleanSupplier transportAvailable) {
        this.nativeSender = nativeSender;
        this.audience = audience;
        this.transportAvailable = transportAvailable;
    }

    /** The native sender behind this one. */
    @NotNull
    public org.bukkit.command.CommandSender getNative() {
        return nativeSender;
    }

    @NotNull
    @Override
    public Audience audience() {
        return audience;
    }

    // Buttons need the Adventure transport; without it the sender receives plain text and can't click anything.
    @Override
    public boolean getSupportsInteraction() {
        return !AudienceExtensions.isConsole(this) && transportAvailable.getAsBoolean();
    }
}
