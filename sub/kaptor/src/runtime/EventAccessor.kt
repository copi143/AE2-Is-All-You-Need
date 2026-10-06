package kaptor.runtime

import kaptor.compiler.EventSchema
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

object EventAccessor {
    private val lookup = MethodHandles.lookup()
    private val mapGetMethod: MethodHandle = lookup.findVirtual(
        Map::class.java, "get",
        MethodType.methodType(Any::class.java, Any::class.java)
    )
    private val mapPutMethod: MethodHandle = lookup.findVirtual(
        MutableMap::class.java, "put",
        MethodType.methodType(Any::class.java, Any::class.java, Any::class.java)
    )

    private val getterCache = ConcurrentHashMap<String, ConcurrentHashMap<String, MethodHandle>>()
    private val setterCache = ConcurrentHashMap<String, ConcurrentHashMap<String, MethodHandle>>()

    fun registerEvent(eventType: String, schema: EventSchema) {
        val getters = ConcurrentHashMap<String, MethodHandle>()
        val setters = ConcurrentHashMap<String, MethodHandle>()
        for (param in schema.parameters) {
            getters[param.name] = MethodHandles.insertArguments(mapGetMethod, 1, param.name)
            setters[param.name] = MethodHandles.insertArguments(mapPutMethod, 1, param.name)
        }
        getterCache[eventType] = getters
        setterCache[eventType] = setters
    }

    fun unregisterEvent(eventType: String) {
        getterCache.remove(eventType)
        setterCache.remove(eventType)
    }

    fun clearAll() {
        getterCache.clear()
        setterCache.clear()
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun getField(event: Any?, fieldName: String): Any? {
        return (event as? Map<String, Any?>)?.get(fieldName)
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun setField(event: Any?, fieldName: String, value: Any?) {
        (event as? MutableMap<String, Any?>)?.set(fieldName, value)
    }
}
