package org.pnfolder.pnlibrary.config.yaml;

import java.lang.reflect.Field;
import java.util.List;

import org.pnfolder.pnlibrary.api.config.ConfigEditable;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;

/** One field marked {@code @ConfigEditable}: its path, the sections to walk through to reach it, and its kind. */
public final class EditableEntry {
    public final String path;
    final List<Field> chain;
    final ConfigEditable annotation;
    public final ConfigSettingKind kind;

    EditableEntry(String path, List<Field> chain, ConfigEditable annotation, ConfigSettingKind kind) {
        this.path = path;
        this.chain = chain;
        this.annotation = annotation;
        this.kind = kind;
    }

    public Field field() {
        return chain.get(chain.size() - 1);
    }
}
