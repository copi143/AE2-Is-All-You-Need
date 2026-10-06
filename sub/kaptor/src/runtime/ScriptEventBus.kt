package kaptor.runtime

import kaptor.ScriptLogger
import kaptor.compiler.EventSchema
import kaptor.createLogger
import kaptor.ir.*
import java.util.concurrent.ConcurrentHashMap

object ScriptEventBus {
    private val beforeHandlers = ConcurrentHashMap<String, MutableList<EventHandlerEntry>>()
    private val onHandlers = ConcurrentHashMap<String, EventHandlerEntry>()
    private val afterHandlers = ConcurrentHashMap<String, MutableList<EventHandlerEntry>>()
    private val eventSchemas = ConcurrentHashMap<String, EventSchema>()
    private val eventLogger: ScriptLogger = createLogger()

    data class EventHandlerEntry(
        val handler: ScriptHandlerBase,
        val eventType: String,
        val hookType: HookType,
        val costLimit: Int,
        val scriptName: String
    )

    fun registerHandler(
        handler: ScriptHandlerBase,
        eventType: String,
        hookType: HookType,
        costLimit: Int,
        scriptName: String
    ) {
        val entry = EventHandlerEntry(handler, eventType, hookType, costLimit, scriptName)
        when (hookType) {
            HookType.BEFORE -> beforeHandlers.computeIfAbsent(eventType) { mutableListOf() }.add(entry)
            HookType.ON -> onHandlers[eventType] = entry
            HookType.AFTER -> afterHandlers.computeIfAbsent(eventType) { mutableListOf() }.add(entry)
        }
        eventLogger.debug("Registered $hookType handler for '$eventType' from '$scriptName' (cost limit: $costLimit)")
    }

    fun unregisterScript(scriptName: String) {
        fun removeFrom(map: MutableMap<String, *>, mutator: (MutableList<EventHandlerEntry>) -> Boolean) {
            val iter = map.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next()

                @Suppress("UNCHECKED_CAST")
                val list = e.value as? MutableList<EventHandlerEntry>
                if (list != null) {
                    list.removeAll { it.scriptName == scriptName }
                    if (list.isEmpty()) iter.remove()
                }
            }
        }
        // Remove single entry
        val onIter = onHandlers.entries.iterator()
        while (onIter.hasNext()) {
            val e = onIter.next()
            if (e.value.scriptName == scriptName) onIter.remove()
        }
        removeFrom(beforeHandlers) { true }
        removeFrom(afterHandlers) { true }
        eventLogger.debug("Unregistered all handlers for script: $scriptName")
    }

    fun clearAll() {
        beforeHandlers.clear()
        onHandlers.clear()
        afterHandlers.clear()
        eventLogger.debug("Cleared all script event handlers")
    }

    fun resetAll() {
        clearAll()
        eventLogger.debug("Reset all script event state")
    }

    fun dispatchEvent(eventType: String, event: Any?) {
        val wrappedEvent = wrapEvent(eventType, event)

        // BEFORE handlers
        beforeHandlers[eventType]?.forEach { entry ->
            dispatchEntry(entry, wrappedEvent)
        }

        // ON handler (with callPrev support)
        val onEntry = onHandlers[eventType]
        if (onEntry != null) {
            ScriptHandlerBase.clearPrevHandler()
            dispatchEntry(onEntry, wrappedEvent)
            ScriptHandlerBase.clearPrevHandler()
        }

        // AFTER handlers
        afterHandlers[eventType]?.forEach { entry ->
            dispatchEntry(entry, wrappedEvent)
        }
    }

    private fun wrapEvent(eventType: String, event: Any?): Any? {
        return event
    }

    private fun dispatchEntry(entry: EventHandlerEntry, event: Any?) {
        try {
            val sandbox = ScriptRuntime.currentSandbox()
            sandbox.reset(entry.costLimit)
            entry.handler.invokeHandler(entry.eventType, event)
            eventLogger.debug(
                "Script ${entry.hookType} handler executed for '${entry.eventType}' from '${entry.scriptName}' " +
                        "(used ${sandbox.getCounter()}/${entry.costLimit} instructions)"
            )
        } catch (e: ScriptLimitException) {
            eventLogger.warn(
                "Script limit exceeded for '${entry.eventType}' from '${entry.scriptName}': ${e.message}"
            )
        } catch (e: Exception) {
            eventLogger.error(
                "Error executing script handler for '${entry.eventType}' from '${entry.scriptName}'",
                e
            )
        }
    }

    fun getRegisteredEventTypes(): Set<String> {
        val keys = mutableSetOf<String>()
        keys.addAll(beforeHandlers.keys)
        keys.addAll(onHandlers.keys)
        keys.addAll(afterHandlers.keys)
        return keys
    }

    fun getHandlerCount(): Int {
        return (beforeHandlers.values.sumOf { it.size }
                + onHandlers.size
                + afterHandlers.values.sumOf { it.size })
    }

    fun getHandlersForType(eventType: String): List<EventHandlerEntry> {
        val result = mutableListOf<EventHandlerEntry>()
        beforeHandlers[eventType]?.let { result.addAll(it) }
        onHandlers[eventType]?.let { result.add(it) }
        afterHandlers[eventType]?.let { result.addAll(it) }
        return result
    }

    fun storeEventSchema(eventType: String, schema: EventSchema) {
        eventSchemas[eventType] = schema
    }

    fun getEventSchema(eventType: String): EventSchema? = eventSchemas[eventType]

    fun buildDeclaredEventsMap(): Map<String, Map<String, IrType>> {
        return eventSchemas.entries.associate { (eventType, schema) ->
            eventType to schema.parameters.associate { param ->
                param.name to classToIrType(param.type)
            }
        }
    }

    private fun classToIrType(clazz: Class<*>): IrType = when (clazz) {
        Int::class.java -> IrIntType
        Long::class.java -> IrLongType
        Float::class.java -> IrFloatType
        Double::class.java -> IrDoubleType
        Boolean::class.java -> IrBoolType
        String::class.java -> IrStringType
        else -> IrObjectType
    }
}
