package org.pnfolder.pnlibrary.bukkit.item;

import java.net.URL;

import org.bukkit.inventory.meta.SkullMeta;

/** Puts a skin from a texture URL on a skull; each server version supports one of the two implementations. */
interface ProfileSetter {
    void apply(SkullMeta meta, URL url) throws ReflectiveOperationException;
}
