package org.pnfolder.pnlibrary.bukkit.commands;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAdventureTransport;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAudienceAdapter;
import org.pnfolder.pnlibrary.spi.commands.GenericPlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.commands.NativeCommandRegistrar;

/** Registers portable commands as Bukkit commands, through the classic command map or Paper's command API. */
public final class BukkitCommandAdapter extends GenericPlatformCommandAdapter<CommandSender> {
    private final NativeCommandRegistrar<CommandSender> nativeRegistrar;
    private final Function<CommandSender, Audience> audience;
    private final BooleanSupplier interactive;

    public BukkitCommandAdapter(BukkitAdventureTransport audiences, Plugin plugin, Supplier<PnLogger> pnLogger,
                                PnLoggerBuffer logBuffer) {
        this(PaperCommandSupport.choose(plugin, new DefaultBukkitNativeCommandRegistrar(), pnLogger, logBuffer),
            sender -> {
                Audience found = new BukkitAudienceAdapter(audiences).of(sender);
                if (found == null) {
                    throw new IllegalArgumentException("Required value was null.");
                }
                return found;
            },
            audiences::isNativeTransportAvailable);
    }

    BukkitCommandAdapter(NativeCommandRegistrar<CommandSender> nativeRegistrar,
                         Function<CommandSender, Audience> audience) {
        this(nativeRegistrar, audience, () -> true);
    }

    BukkitCommandAdapter(NativeCommandRegistrar<CommandSender> nativeRegistrar,
                         Function<CommandSender, Audience> audience, BooleanSupplier interactive) {
        this.nativeRegistrar = nativeRegistrar;
        this.audience = audience;
        this.interactive = interactive;
    }

    @NotNull
    @Override
    protected NativeCommandRegistrar<CommandSender> getRegistrar() {
        return nativeRegistrar;
    }

    @NotNull
    @Override
    protected CommandContext context(@NotNull CommandSender source, @NotNull String alias,
                                     @NotNull String[] arguments) {
        String last = arguments.length == 0 ? "" : arguments[arguments.length - 1];
        return new CommandContext(new BukkitCommandSender(source, audience.apply(source), interactive),
            Arrays.asList(arguments), alias, last);
    }
}
