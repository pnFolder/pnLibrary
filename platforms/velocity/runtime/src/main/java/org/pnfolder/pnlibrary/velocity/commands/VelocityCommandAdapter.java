package org.pnfolder.pnlibrary.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyCommandContext;
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;
import org.pnfolder.pnlibrary.velocity.network.VelocityAudienceAdapter;

import java.util.function.Function;

/** Registers pnLibrary commands on Velocity and turns native command sources into portable audiences. */
public final class VelocityCommandAdapter extends GenericPlatformCommandAdapter<CommandSource> {
    private final NativeCommandRegistrar<CommandSource> nativeRegistrar;
    private final Function<CommandSource, Audience> audience;

    /** Creates the adapter that registers real Velocity commands and resolves sources through {@code audiences}. */
    public VelocityCommandAdapter(@NotNull ProxyServer server, @NotNull VelocityAudienceAdapter audiences) {
        this(new VelocityNativeCommandRegistrar(server), source -> {
            Audience found = audiences.of(source);
            if (found == null) {
                throw new IllegalArgumentException(
                        "Unsupported Velocity command source: " + source.getClass().getName());
            }
            return found;
        });
    }

    VelocityCommandAdapter(
            @NotNull NativeCommandRegistrar<CommandSource> nativeRegistrar,
            @NotNull Function<CommandSource, Audience> audience) {
        this.nativeRegistrar = nativeRegistrar;
        this.audience = audience;
    }

    @NotNull
    @Override
    protected NativeCommandRegistrar<CommandSource> getRegistrar() {
        return nativeRegistrar;
    }

    @NotNull
    @Override
    protected CommandContext context(
            @NotNull CommandSource source, @NotNull String alias, @NotNull String[] arguments) {
        return ProxyCommandContext.of(audience.apply(source), alias, arguments);
    }
}
