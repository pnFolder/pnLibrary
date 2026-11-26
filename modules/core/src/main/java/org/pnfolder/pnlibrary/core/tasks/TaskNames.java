package org.pnfolder.pnlibrary.core.tasks;

/** Names of the owner, the code and the failures of a task, as shown in its snapshot. */
final class TaskNames {
    private TaskNames() {
    }

    /** The class that holds the action; a lambda reports the class it was written in. */
    static String sourceOf(Class<?> type) {
        String name = type.getName();
        int lambda = name.indexOf("$$Lambda");
        String outer = lambda < 0 ? name : name.substring(0, lambda);
        int slash = outer.indexOf('/');
        return slash < 0 ? outer : outer.substring(0, slash);
    }

    static String ownerName(Object owner) {
        String simple = owner.getClass().getSimpleName();
        return simple.trim().isEmpty() ? owner.toString() : simple;
    }

    static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + (error.getMessage() == null ? "" : error.getMessage());
    }
}
