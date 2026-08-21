package allyouneed.parts.logger

import java.util.IdentityHashMap

object NetworkLogSettle {
    private class Leave(val loggerId: Int, val entry: NetworkLogEntry)

    private val leaves = IdentityHashMap<Any, Leave>()
    private val moved = IdentityHashMap<Any, Boolean>()
    private var flushed = false

    fun noteLeave(owner: Any, loggerId: Int, entry: NetworkLogEntry) {
        leaves[owner] = Leave(loggerId, entry)
    }

    fun noteJoin(owner: Any): Boolean {
        if (leaves.remove(owner) == null) return false
        moved[owner] = java.lang.Boolean.TRUE
        return true
    }

    fun wasMoved(owner: Any): Boolean = moved.containsKey(owner)

    fun flushIfNeeded() {
        if (flushed) return
        flushed = true
        if (leaves.isNotEmpty()) {
            val batch = ArrayList(leaves.values)
            leaves.clear()
            for (leave in batch) {
                if (leave.loggerId != 0) {
                    LogStore.append(leave.loggerId, leave.entry)
                }
            }
        }
        moved.clear()
    }

    fun beginTick() {
        flushed = false
    }
}
