package org.pnfolder.pnlibrary.bukkit.item;

import java.lang.reflect.Method;

/** Looks up classes and methods by name; a seam so tests can count the lookups. */
interface Reflection {
    Class<?> type(String name) throws ClassNotFoundException;

    Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException;

    /** The real lookup. */
    final class Runtime implements Reflection {
        static final Runtime INSTANCE = new Runtime();

        private Runtime() {
        }

        @Override
        public Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName(name);
        }

        @Override
        public Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
            return type.getMethod(name, parameters);
        }
    }
}
