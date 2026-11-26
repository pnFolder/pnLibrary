package org.pnfolder.pnlibrary.core.commands.proxy;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandSender;

/** Delegates portable command replies to the native audience bridge, rewriting {@code /pn} for the proxy control command. */
final class ProxyCommandSender implements CommandSender, ForwardingAudience.Single {
    private final Audience audience;
    private final boolean proxyControl;

    ProxyCommandSender(Audience audience, boolean proxyControl) {
        this.audience = audience;
        this.proxyControl = proxyControl;
    }

    @NotNull
    @Override
    public Audience audience() {
        return audience;
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        audience.sendMessage(proxyControl ? ProxyCommandNames.rewrite(message) : message);
    }
}
