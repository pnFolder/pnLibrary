package org.pnfolder.pnlibrary.api.events

/**
 * Optional explanation of an [Event] class. Menus and `/pn bus` show it next to the event, so an administrator
 * learns what the event is for without reading the plugin's code.
 *
 * @property purpose what happens when the event is called, in one sentence
 * @property usage how a plugin is expected to use it, for example which results a listener may set; may stay empty
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class EventDescription(val purpose: String, val usage: String = "")
