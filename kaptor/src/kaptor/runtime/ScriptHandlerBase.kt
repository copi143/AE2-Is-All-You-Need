package kaptor.runtime

abstract class ScriptHandlerBase {
    companion object {
        private val prevHandler = ThreadLocal<(Any?) -> Unit>()

        fun setPrevHandler(handler: (Any?) -> Unit) {
            prevHandler.set(handler)
        }

        fun clearPrevHandler() {
            prevHandler.remove()
        }

        fun getPrevHandler(): ((Any?) -> Unit)? = prevHandler.get()
    }

    abstract fun getEventTypes(): Array<String>

    open fun getCostLimits(): Map<String, Int> = emptyMap()

    open fun invokeHandler(eventTypeName: String, event: Any?) {
        val sandbox = ScriptRuntime.currentSandbox()
        val limit = getCostLimits()[eventTypeName] ?: 1000
        sandbox.reset(limit)

        try {
            val sanitized = eventTypeName.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val methodNames = listOf("handle_$sanitized", "before_$sanitized", "after_$sanitized")
            var found = false
            for (name in methodNames) {
                try {
                    val method = this.javaClass.getMethod(name, Any::class.java)
                    method.invoke(this, event)
                    found = true
                } catch (_: NoSuchMethodException) {}
            }
            if (!found) {
                throw ScriptLimitException("No handler for event type: $eventTypeName")
            }
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            if (cause is ScriptLimitException) throw cause
            if (cause is RuntimeException) throw cause
            throw RuntimeException("Script error in handler for $eventTypeName", cause)
        }
    }

    open fun callPrev(event: Any?): Any? {
        val prev = getPrevHandler()
        prev?.invoke(event)
        return null
    }
}
