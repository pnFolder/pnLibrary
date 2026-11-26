package org.pnfolder.pnlibrary.bungee.commands;

import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.md_5.bungee.api.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.bungee.network.BungeeAudienceAdapter;
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyCommandContext;
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;

import java.util.function.Function;

/** Registers pnLibrary commands on BungeeCord and turns native senders into portable audiences. */
public final class BungeeCommandAdapter extends GenericPlatformCommandAdapter<CommandSender> {
    private final NativeCommandRegistrar<CommandSender> nativeRegistrar;
    private final Function<CommandSender, Audience> audience;

    /** Creates the adapter that registers real Bungee commands and resolves senders through {@code audiences}. */
    public BungeeCommandAdapter(@NotNull BungeeAudienceAdapter audiences) {
        this(new BungeeNativeCommandRegistrar(), sender -> {
            Audience found = audiences.of(sender);
            if (found == null) {
                throw new IllegalArgumentException("Required value was null.");
            }
            return found;
        });
    }

    BungeeCommandAdapter(
            @NotNull NativeCommandRegistrar<CommandSender> nativeRegistrar,
            @NotNull Function<CommandSender, Audience> audience) {
        this.nativeRegistrar = Objects.requireNonNull(nativeRegistrar, "nativeRegistrar");
        this.audience = Objects.requireNonNull(audience, "audience");
    }

    @NotNull
    @Override
    protected NativeCommandRegistrar<CommandSender> getRegistrar() {
        return nativeRegistrar;
    }

    @NotNull
    @Override
    protected CommandContext context(
            @NotNull CommandSender source, @NotNull String alias, @NotNull String[] arguments) {
        return ProxyCommandContext.of(audience.apply(source), alias, arguments);
    }
}
