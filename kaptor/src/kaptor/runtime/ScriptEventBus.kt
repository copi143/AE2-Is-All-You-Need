package kaptor.runtime

import kaptor.createLogger
import kaptor.ScriptLogger
import java.util.concurrent.ConcurrentHashMap

object ScriptEventBus {
    private val handlers = ConcurrentHashMap<String, MutableList<EventHandlerEntry>>()
    private val eventClassMap = ConcurrentHashMap<String, Class<*>>()
    private val eventLogger: ScriptLogger = createLogger()

    data class EventHandlerEntry(
        val handler: ScriptHandlerBase,
        val eventType: String,
        val costLimit: Int,
        val scriptName: String
    )

    fun registerHandler(handler: ScriptHandlerBase, eventType: String, costLimit: Int, scriptName: String) {
        val entry = EventHandlerEntry(handler, eventType, costLimit, scriptName)
        handlers.computeIfAbsent(eventType) { mutableListOf() }.add(entry)
        eventLogger.debug("Registered script handler for '$eventType' from '$scriptName' (cost limit: $costLimit)")
    }

    fun unregisterScript(scriptName: String) {
        val iterator = handlers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.removeAll { it.scriptName == scriptName }
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
        eventLogger.debug("Unregistered all handlers for script: $scriptName")
    }

    fun clearAll() {
        handlers.clear()
        eventLogger.debug("Cleared all script event handlers")
    }

    fun dispatchEvent(eventType: String, event: Any?) {
        val entries = handlers[eventType] ?: return
        for (entry in entries) {
            try {
                val sandbox = ScriptRuntime.currentSandbox()
                sandbox.reset(entry.costLimit)
                entry.handler.invokeHandler(entry.eventType, event)
                eventLogger.debug(
                    "Script handler executed for '$eventType' from '${entry.scriptName}' " +
                    "(used ${sandbox.getCounter()}/${entry.costLimit} instructions)"
                )
            } catch (e: ScriptLimitException) {
                eventLogger.warn(
                    "Script limit exceeded for '$eventType' from '${entry.scriptName}': ${e.message}"
                )
            } catch (e: Exception) {
                eventLogger.error(
                    "Error executing script handler for '$eventType' from '${entry.scriptName}'",
                    e
                )
            }
        }
    }

    fun getRegisteredEventTypes(): Set<String> = handlers.keys.toSet()

    fun getHandlerCount(): Int = handlers.values.sumOf { it.size }

    fun getHandlersForType(eventType: String): List<EventHandlerEntry> {
        return handlers[eventType]?.toList() ?: emptyList()
    }

    fun registerEventClass(eventType: String, clazz: Class<*>) {
        eventClassMap[eventType] = clazz
    }

    fun getEventClass(eventType: String): Class<*>? = eventClassMap[eventType]
}
