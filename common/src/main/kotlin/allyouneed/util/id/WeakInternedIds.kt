package allyouneed.util.id

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

internal class WeakInternedIds<T : Any>(
    private val hashOf: (T) -> Int,
    private val eq: (T, T) -> Boolean,
) {
    private val lock = Any()
    private val queue = ReferenceQueue<T>()
    private val free = ArrayDeque<Int>()
    private val slots = ArrayList<Slot?>()
    private var table = arrayOfNulls<Slot>(INITIAL_CAP)
    private var liveSlots = 0

    fun assign(key: T): Int = synchronized(lock) {
        expunge()
        val hash = hashOf(key)
        val existing = findSlot(key, hash)
        if (existing != null) {
            if (!alreadyWatched(existing, key)) addWatch(existing, key)
            return existing.id
        }
        val id = allocId()
        val slot = Slot(id, hash)
        insert(slot)
        slots[id] = slot
        addWatch(slot, key)
        return id
    }

    fun peek(key: T): Int = synchronized(lock) {
        expunge()
        return findSlot(key, hashOf(key))?.id ?: -1
    }

    fun find(id: Int): T? = synchronized(lock) {
        expunge()
        val slot = slots.getOrNull(id) ?: return null
        compactDeadWatches(slot)
        if (slot.watchCount == 0) {
            recycle(slot)
            return null
        }
        var watch = slot.watches
        while (watch != null) {
            val live = liveOf(watch)
            if (live != null) return live
            watch = watch.link
        }
        return null
    }

    fun size(): Int = synchronized(lock) {
        expunge()
        compactAll()
        return liveSlots
    }

    fun contains(id: Int): Boolean = find(id) != null

    fun clear(onLive: ((T) -> Unit)? = null) = synchronized(lock) {
        if (onLive != null) {
            for (slot in slots) {
                if (slot == null) continue
                var watch = slot.watches
                while (watch != null) {
                    liveOf(watch)?.let(onLive)
                    watch = watch.link
                }
            }
        }
        while (queue.poll() != null) {
        }
        slots.clear()
        free.clear()
        table = arrayOfNulls(INITIAL_CAP)
        liveSlots = 0
    }

    private fun allocId(): Int {
        val recycled = free.removeLastOrNull()
        if (recycled != null) return recycled
        val id = slots.size
        slots.add(null)
        return id
    }

    private fun insert(slot: Slot) {
        if (liveSlots >= table.size - (table.size ushr 2)) resize()
        val i = index(slot.hash)
        slot.tableNext = table[i]
        table[i] = slot
        liveSlots++
    }

    private fun resize() {
        val next = arrayOfNulls<Slot>(table.size shl 1)
        val mask = next.size - 1
        for (slot in slots) {
            if (slot == null) continue
            val i = mix(slot.hash) and mask
            slot.tableNext = next[i]
            next[i] = slot
        }
        table = next
    }

    private fun findSlot(key: T, hash: Int): Slot? {
        val i = index(hash)
        var prev: Slot? = null
        var cur = table[i]
        while (cur != null) {
            val next = cur.tableNext
            if (cur.hash == hash) {
                compactDeadWatches(cur)
                if (cur.watchCount == 0) {
                    if (prev == null) table[i] = next else prev.tableNext = next
                    retire(cur)
                    cur = next
                    continue
                }
                if (matches(cur, key)) return cur
            }
            prev = cur
            cur = next
        }
        return null
    }

    private fun matches(slot: Slot, key: T): Boolean {
        var watch = slot.watches
        while (watch != null) {
            val live = liveOf(watch)
            if (live != null && eq(live, key)) return true
            watch = watch.link
        }
        return false
    }

    private fun alreadyWatched(slot: Slot, key: T): Boolean {
        var watch = slot.watches
        while (watch != null) {
            if (watch.get() === key) return true
            watch = watch.link
        }
        return false
    }

    private fun addWatch(slot: Slot, key: T) {
        val watch = Watch(key, queue, slot)
        watch.link = slot.watches
        slot.watches = watch
        slot.watchCount++
    }

    private fun compactDeadWatches(slot: Slot) {
        var prev: Watch<*>? = null
        var cur = slot.watches
        while (cur != null) {
            val next = cur.link
            if (cur.get() == null) {
                if (prev == null) slot.watches = next else prev.link = next
                slot.watchCount--
            } else {
                prev = cur
            }
            cur = next
        }
    }

    private fun unlinkWatch(slot: Slot, target: Watch<*>) {
        var prev: Watch<*>? = null
        var cur = slot.watches
        while (cur != null) {
            if (cur === target) {
                if (prev == null) slot.watches = cur.link else prev.link = cur.link
                slot.watchCount--
                return
            }
            prev = cur
            cur = cur.link
        }
    }

    private fun recycle(slot: Slot) {
        val i = index(slot.hash)
        var prev: Slot? = null
        var cur = table[i]
        while (cur != null) {
            val next = cur.tableNext
            if (cur === slot) {
                if (prev == null) table[i] = next else prev.tableNext = next
                break
            }
            prev = cur
            cur = next
        }
        retire(slot)
    }

    private fun retire(slot: Slot) {
        if (slots.getOrNull(slot.id) === slot) {
            slots[slot.id] = null
            liveSlots--
            free.addLast(slot.id)
        }
    }

    private fun compactAll() {
        for (index in slots.indices) {
            val slot = slots[index] ?: continue
            compactDeadWatches(slot)
            if (slot.watchCount == 0) recycle(slot)
        }
    }

    private fun expunge() {
        while (true) {
            val watch = queue.poll() as Watch<*>? ?: break
            val slot = watch.slot
            unlinkWatch(slot, watch)
            if (slot.watchCount == 0 && slots.getOrNull(slot.id) === slot) {
                recycle(slot)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun liveOf(watch: Watch<*>): T? = watch.get() as T?

    private fun index(hash: Int) = mix(hash) and (table.size - 1)

    private fun mix(hash: Int) = hash xor (hash ushr 16)

    private class Slot(val id: Int, val hash: Int) {
        var tableNext: Slot? = null
        var watches: Watch<*>? = null
        var watchCount = 0
    }

    private class Watch<V : Any>(
        referent: V,
        queue: ReferenceQueue<V>,
        val slot: Slot,
    ) : WeakReference<V>(referent, queue) {
        var link: Watch<*>? = null
    }

    private companion object {
        const val INITIAL_CAP = 32
    }
}
