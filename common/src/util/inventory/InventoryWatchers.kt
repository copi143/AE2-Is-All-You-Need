package allyouneed.util.inventory

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.WeakHashMap

object InventoryWatchers {
    private val lock = Any()
    private val reentering = ThreadLocal.withInitial { false }
    private val byPos = HashMap<PosKey, ArrayList<Runnable>>()
    private val handlerToPos = WeakHashMap<Any, PosKey>()
    private val childHandlers = WeakHashMap<Any, Any>()

    @JvmStatic
    fun watch(level: Level, pos: BlockPos, handlers: Collection<Any?>, onChange: Runnable): Handle =
        watchAt(System.identityHashCode(level), pos.asLong(), handlers, onChange)

    @JvmStatic
    fun watchAt(levelId: Int, packedPos: Long, handlers: Collection<Any?>, onChange: Runnable): Handle {
        val key = PosKey(levelId, packedPos)
        val registered = ArrayList<Any>()
        synchronized(lock) {
            byPos.getOrPut(key) { ArrayList() }.add(onChange)
            for (handler in handlers) {
                if (handler == null) continue
                handlerToPos[handler] = key
                registered.add(handler)
                val child = childHandlers[handler]
                if (child != null) {
                    handlerToPos[child] = key
                    registered.add(child)
                }
            }
        }
        return Handle(key, onChange, registered.toTypedArray())
    }

    @JvmStatic
    fun linkChild(parent: Any?, child: Any?) {
        if (parent == null || child == null) return
        synchronized(lock) {
            childHandlers[parent] = child
        }
    }

    @JvmStatic
    fun unwatch(handle: Handle?) {
        if (handle == null || handle.closed) return
        handle.closed = true
        synchronized(lock) {
            val list = byPos[handle.key] ?: return
            list.remove(handle.onChange)
            val empty = list.isEmpty()
            if (empty) byPos.remove(handle.key)
            if (empty) {
                for (handler in handle.handlers) {
                    if (handlerToPos[handler] == handle.key) handlerToPos.remove(handler)
                }
            }
        }
    }

    @JvmStatic
    fun notifyPos(level: Level?, pos: BlockPos?) {
        if (level == null || pos == null || level.isClientSide) return
        fire(PosKey(System.identityHashCode(level), pos.asLong()))
    }

    @JvmStatic
    fun notifyAt(levelId: Int, packedPos: Long) {
        fire(PosKey(levelId, packedPos))
    }

    @JvmStatic
    fun notifyHandler(handler: Any?) {
        if (handler == null) return
        val key = synchronized(lock) { handlerToPos[handler] } ?: return
        fire(key)
    }

    @JvmStatic
    fun notifyOwner(owner: Any?) {
        if (owner == null) return
        notifyHandler(owner)
    }

    private fun fire(key: PosKey) {
        if (reentering.get()) return
        val listeners = synchronized(lock) { byPos[key]?.let { ArrayList(it) } } ?: return
        if (listeners.isEmpty()) return
        reentering.set(true)
        try {
            for (listener in listeners) listener.run()
        } finally {
            reentering.set(false)
        }
    }

    data class PosKey(val levelId: Int, val pos: Long)

    class Handle internal constructor(
        internal val key: PosKey,
        internal val onChange: Runnable,
        internal val handlers: Array<Any>,
    ) {
        @JvmField
        var closed: Boolean = false
    }
}
