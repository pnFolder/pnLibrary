package org.pnfolder.pnlibrary.api.config

/**
 * Rewrites a YAML object into the object the polymorphic codec expects, before its `type` is read.
 *
 * Declare it with [ConfigNodeForm] on the polymorphic base type. It lets an object without a `type`
 * (a wrapper such as `delay: 2s` with a `do:` list) stand where an action is accepted.
 *
 * Implementations return [node] unchanged when they do not recognise it.
 */
interface ConfigNodeRewriter {
    /** Returns the object to decode in place of [node]; [path] is the location used in messages. */
    fun rewrite(node: Map<String, Any?>, path: String): Map<String, Any?>
}
