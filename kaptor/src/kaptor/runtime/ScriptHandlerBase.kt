package kaptor.runtime

abstract class ScriptHandlerBase {
    abstract fun getEventTypes(): Array<String>

    open fun getCostLimits(): Map<String, Int> = emptyMap()

    fun invokeHandler(eventTypeName: String, event: Any?) {
        val sandbox = ScriptRuntime.currentSandbox()
        val limit = getCostLimits()[eventTypeName] ?: 1000
        sandbox.reset(limit)

        try {
            when (eventTypeName) {
                else -> {
                    val methodName = "handle_${eventTypeName.replace(Regex("[^a-zA-Z0-9_]"), "_")}"
                    try {
                        val method = this.javaClass.getMethod(methodName, Any::class.java)
                        method.invoke(this, event)
                    } catch (e: NoSuchMethodException) {
                        throw ScriptLimitException("No handler for event type: $eventTypeName")
                    }
                }
            }
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            if (cause is ScriptLimitException) throw cause
            if (cause is RuntimeException) throw cause
            throw RuntimeException("Script error in handler for $eventTypeName", cause)
        }
    }
}
