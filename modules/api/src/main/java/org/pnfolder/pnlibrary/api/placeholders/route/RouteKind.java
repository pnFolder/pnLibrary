package org.pnfolder.pnlibrary.api.placeholders.route;

/** What a part of an address is. For {@code {clan.war.top.3.name|pad0:4}}: group, branch, branch, argument, field, format. */
public enum RouteKind {
    /** The first word, the one of {@code @Placeholders("clan")}. */
    GROUP,
    /** A word that continues to other words: a method or property that returns an object. */
    BRANCH,
    /** A word that fills an {@code ArgumentValue} parameter: the {@code 3} above. */
    ARGUMENT,
    /** The last word, the one that has the value. */
    FIELD,
    /** A part after a bar: {@code pad0:4}. */
    FORMAT
}
