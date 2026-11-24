package org.pnfolder.pnlibrary.core.placeholders

/** How one parameter of a placeholder method is filled from the call. */
internal sealed class ParameterPlan {
    abstract fun value(call: Call): Any?

    object ViewerParameter : ParameterPlan() {
        override fun value(call: Call): Any? =
            call.viewer
    }

    object RouteParameter : ParameterPlan() {
        override fun value(call: Call): Any? = call.route
    }

    class One(private val name: String, private val spec: ArgumentSpec) : ParameterPlan() {
        override fun value(call: Call): Any? = call.slot(name).argument
    }

    class Many(private val constructor: java.lang.reflect.Constructor<*>, private val parts: List<Pair<String, ArgumentSpec>>) : ParameterPlan() {
        override fun value(call: Call): Any? =
            constructor.newInstance(*parts.map { (name, _) -> call.slot(name).argument }.toTypedArray())
    }
}
