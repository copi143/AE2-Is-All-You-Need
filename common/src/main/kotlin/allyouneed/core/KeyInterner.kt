package allyouneed.core

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

object KeyInterner {
    private val lock = Any()
    private val queue = ReferenceQueue<Any>()
    private var buckets = arrayOfNulls<Slot>(INITIAL_CAP)
    private var live = 0

    @JvmStatic
    fun intern(key: Any): Any = synchronized(lock) {
        expunge()
        val h = hashOf(key)
        val existing = find(key, h)
        if (existing != null) return existing
        insert(key, h)
        live++
        if (live > buckets.size - (buckets.size ushr 2)) resize()
        key
    }

    @JvmStatic
    fun size(): Int = synchronized(lock) {
        expunge()
        live
    }

    @JvmStatic
    fun clear() = synchronized(lock) {
        buckets = arrayOfNulls(INITIAL_CAP)
        live = 0
        while (queue.poll() != null) {
        }
    }

    private fun hashOf(key: Any): Int =
        if (key is ContentIdentity) key.`asm$hashCode`() else key.hashCode()

    private fun eq(a: Any, b: Any): Boolean =
        if (a is ContentIdentity) a.`asm$equals`(b) else a == b

    private fun find(key: Any, h: Int): Any? {
        var s = buckets[index(h)]
        while (s != null) {
            val v = s.ref.get()
            if (v != null && s.hash == h && eq(v, key)) return v
            s = s.next
        }
        return null
    }

    private fun insert(key: Any, h: Int) {
        val i = index(h)
        buckets[i] = Slot(h, Ref(key, h, queue), buckets[i])
    }

    private fun expunge() {
        while (true) {
            val ref = queue.poll() as? Ref ?: break
            unlink(ref)
            live--
        }
    }

    private fun unlink(ref: Ref) {
        val i = index(ref.hash)
        var prev: Slot? = null
        var cur = buckets[i]
        while (cur != null) {
            if (cur.ref === ref) {
                if (prev == null) buckets[i] = cur.next else prev.next = cur.next
                return
            }
            prev = cur
            cur = cur.next
        }
    }

    private fun resize() {
        val old = buckets
        buckets = arrayOfNulls(old.size shl 1)
        var kept = 0
        for (head in old) {
            var s = head
            while (s != null) {
                val next = s.next
                if (s.ref.get() != null) {
                    val i = index(s.hash)
                    s.next = buckets[i]
                    buckets[i] = s
                    kept++
                }
                s = next
            }
        }
        live = kept
    }

    private fun index(h: Int): Int = mix(h) and (buckets.size - 1)

    private fun mix(h: Int): Int = h xor (h ushr 16)

    private class Slot(val hash: Int, val ref: Ref, var next: Slot?)

    private class Ref(referent: Any, val hash: Int, queue: ReferenceQueue<Any>) : WeakReference<Any>(referent, queue)

    private const val INITIAL_CAP = 16
}
