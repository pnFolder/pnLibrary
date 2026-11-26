package org.pnfolder.pnlibrary.config.yaml;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigSerializationContext;

/** Creates the context a custom serializer receives for one value. */
interface SerializationContexts {
    @NotNull
    ConfigSerializationContext create(@NotNull Type declaredType, @NotNull String path,
                                      @NotNull List<Annotation> annotations, @Nullable Object defaultValue);
}
