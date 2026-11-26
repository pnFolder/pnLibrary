package org.pnfolder.pnlibrary.config.yaml;

import java.lang.reflect.Field;
import java.util.List;

import org.pnfolder.pnlibrary.api.config.ConfigEditable;
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind;

/** One field marked {@code @ConfigEditable}: its path, the sections to walk through to reach it, and its kind. */
final class EditableEntry {
    final String path;
    final List<Field> chain;
    final ConfigEditable annotation;
    final ConfigSettingKind kind;

    EditableEntry(String path, List<Field> chain, ConfigEditable annotation, ConfigSettingKind kind) {
        this.path = path;
        this.chain = chain;
        this.annotation = annotation;
        this.kind = kind;
    }

    Field field() {
        return chain.get(chain.size() - 1);
    }
}
