package org.pnfolder.pnlibrary.core.commands.modules;

/** A module context whose Minecraft version check an administrator can switch off ("start anyway"). */
public interface MinecraftForcible {
    void forceMinecraft(boolean on);
}
