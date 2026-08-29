package allyouneed.util.bigint

import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap
import it.unimi.dsi.fastutil.objects.AbstractObjectSet
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.ObjectIterator
import it.unimi.dsi.fastutil.objects.ObjectSet
import java.io.Serializable
import java.math.BigInteger
import java.util.Arrays
import java.util.NoSuchElementException
import java.util.function.Consumer

class ObjectCounter<K>(expected: Int = 16, val f: Float = Hash.DEFAULT_LOAD_FACTOR) :
    AbstractObject2ObjectMap<K, Counter>(), Serializable, Cloneable, Hash {

    @Transient
    private var n: Int = HashCommon.arraySize(expected, f)

    @Transient
    private var mask: Int = n - 1

    @Transient
    private var maxFill: Int = HashCommon.maxFill(n, f)

    @Transient
    private val minN: Int = n

    @Transient
    private var containsNullKey: Boolean = false

    @Transient
    private var key: Array<Any?> = arrayOfNulls(n + 1)

    @Transient
    private var lo: LongArray = LongArray(n + 1)

    @Transient
    private var hi: LongArray = LongArray(n + 1)

    @Transient
    private var bi: Array<BigInteger?> = arrayOfNulls(n + 1)

    @Transient
    override var size: Int = 0
        private set

    init {
        if (f <= 0 || 1 <= f) throw IllegalArgumentException("Load factor must be greater than 0 and smaller than 1")
        if (expected < 0) throw IllegalArgumentException("The expected number of elements must be nonnegative")
        defaultReturnValue(Counter.ZERO)
    }

    private fun getCounterAt(pos: Int): Counter {
        val b = bi[pos]
        if (b != null) return Counter.of(b)
        val l = lo[pos].toULong()
        val h = hi[pos].toULong()
        if (h == 0UL && l == 0UL) return Counter.ZERO
        return Counter.fromRaw(l, h)
    }

    private fun getCounterAtNull(): Counter {
        val b = bi[n]
        if (b != null) return Counter.of(b)
        val l = lo[n].toULong()
        val h = hi[n].toULong()
        if (h == 0UL && l == 0UL) return Counter.ZERO
        return Counter.fromRaw(l, h)
    }

    private fun setCounterAt(pos: Int, c: Counter) {
        val b = c.bi
        if (b != null) {
            lo[pos] = -1L
            hi[pos] = -1L
            bi[pos] = b
        } else {
            lo[pos] = c.lo.toLong()
            hi[pos] = c.hi.toLong()
            bi[pos] = null
        }
    }

    private fun realSize(): Int = if (containsNullKey) size - 1 else size

    private fun find(k: Any?): Int {
        if (k == null) return if (containsNullKey) n else -(n + 1)
        var pos = HashCommon.mix(k.hashCode()) and mask
        var curr = key[pos]
        if (curr == null) return -(pos + 1)
        if (k == curr) return pos
        while (true) {
            pos = (pos + 1) and mask
            curr = key[pos]
            if (curr == null) return -(pos + 1)
            if (k == curr) return pos
        }
    }

    private fun insert(pos: Int, k: K, v: Counter) {
        if (pos == n) containsNullKey = true
        key[pos] = k
        setCounterAt(pos, v)
        if (size++ >= maxFill) rehash(HashCommon.arraySize(size + 1, f))
    }

    private fun removeEntry(pos: Int): Counter {
        val old = getCounterAt(pos)
        size--
        shiftKeys(pos)
        if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) rehash(n / 2)
        return old
    }

    private fun removeNullEntry(): Counter {
        containsNullKey = false
        val old = getCounterAtNull()
        key[n] = null
        lo[n] = 0L
        hi[n] = 0L
        bi[n] = null
        size--
        if (n > minN && size < maxFill / 4 && n > Hash.DEFAULT_INITIAL_SIZE) rehash(n / 2)
        return old
    }

    private fun shiftKeys(pos: Int) {
        var posVar = pos
        var last: Int
        var slot: Int
        var curr: Any?
        val keyArr = key
        while (true) {
            last = posVar
            posVar = (posVar + 1) and mask
            while (true) {
                curr = keyArr[posVar]
                if (curr == null) {
                    keyArr[last] = null
                    lo[last] = 0L
                    hi[last] = 0L
                    bi[last] = null
                    return
                }
                slot = HashCommon.mix(curr.hashCode()) and mask
                if (if (last <= posVar) last >= slot || slot > posVar else last >= slot && slot > posVar) break
                posVar = (posVar + 1) and mask
            }
            keyArr[last] = curr
            lo[last] = lo[posVar]
            hi[last] = hi[posVar]
            bi[last] = bi[posVar]
        }
    }

    override fun put(k: K, v: Counter): Counter? {
        val pos = find(k)
        if (pos < 0) {
            insert(-pos - 1, k, v)
            return defRetValue
        }
        val old = getCounterAt(pos)
        setCounterAt(pos, v)
        return old
    }

    override fun get(key: K): Counter? {
        if (key == null) return if (containsNullKey) getCounterAtNull() else defRetValue
        var pos = HashCommon.mix((key as Any).hashCode()) and mask
        var curr = this.key[pos]
        if (curr == null) return defRetValue
        if (key == curr) return getCounterAt(pos)
        while (true) {
            pos = (pos + 1) and mask
            curr = this.key[pos]
            if (curr == null) return defRetValue
            if (key == curr) return getCounterAt(pos)
        }
    }

    override fun remove(key: K): Counter? {
        if (key == null) return if (containsNullKey) removeNullEntry() else defRetValue
        var pos = HashCommon.mix((key as Any).hashCode()) and mask
        var curr = this.key[pos]
        if (curr == null) return defRetValue
        if (key == curr) return removeEntry(pos)
        while (true) {
            pos = (pos + 1) and mask
            curr = this.key[pos]
            if (curr == null) return defRetValue
            if (key == curr) return removeEntry(pos)
        }
    }

    override fun containsKey(key: K): Boolean {
        if (key == null) return containsNullKey
        var pos = HashCommon.mix((key as Any).hashCode()) and mask
        var curr = this.key[pos] ?: return false
        if (key == curr) return true
        while (true) {
            pos = (pos + 1) and mask
            curr = this.key[pos] ?: return false
            if (key == curr) return true
        }
    }

    override fun containsValue(value: Counter): Boolean {
        if (containsNullKey && getCounterAtNull() == value) return true
        for (i in 0 until n) if (key[i] != null && getCounterAt(i) == value) return true
        return false
    }

    override fun clear() {
        if (isEmpty()) return
        size = 0
        containsNullKey = false
        Arrays.fill(key, null)
        Arrays.fill(lo, 0L)
        Arrays.fill(hi, 0L)
        Arrays.fill(bi, null)
    }

    override fun isEmpty(): Boolean = size == 0

    fun trim(): Boolean = trim(size)
    fun trim(expected: Int): Boolean {
        val l = HashCommon.nextPowerOfTwo(Math.ceil(expected / f.toDouble()).toInt())
        return l >= n || size > HashCommon.maxFill(l, f) || try {
            rehash(l); true
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    fun rehash(newN: Int) {
        val keyCopy = key
        val loCopy = lo
        val hiCopy = hi
        val biCopy = bi
        val maskNew = newN - 1
        val newKey = arrayOfNulls<Any?>(newN + 1)
        val newLo = LongArray(newN + 1)
        val newHi = LongArray(newN + 1)
        val newBi = arrayOfNulls<BigInteger?>(newN + 1)
        var i = n
        var count = realSize()
        while (count-- != 0) {
            while (keyCopy[--i] == null);
            var pos = HashCommon.mix((keyCopy[i] as Any).hashCode()) and maskNew
            while (newKey[pos] != null) pos = (pos + 1) and maskNew
            newKey[pos] = keyCopy[i]
            newLo[pos] = loCopy[i]
            newHi[pos] = hiCopy[i]
            newBi[pos] = biCopy[i]
        }
        newBi[newN] = biCopy[n]
        newLo[newN] = loCopy[n]
        newHi[newN] = hiCopy[n]
        newKey[newN] = keyCopy[n]
        n = newN
        mask = maskNew
        maxFill = HashCommon.maxFill(n, f)
        key = newKey
        lo = newLo
        hi = newHi
        bi = newBi
    }

    override fun clone(): ObjectCounter<K> {
        val c = super.clone() as ObjectCounter<K>
        c.key = key.clone()
        c.lo = lo.clone()
        c.hi = hi.clone()
        c.bi = bi.clone()
        c.containsNullKey = containsNullKey
        return c
    }

    override fun hashCode(): Int {
        var h = 0
        var j = realSize()
        var i = 0
        while (j-- != 0) {
            while (key[i] == null) i++
            var t = (key[i] as Any).hashCode()
            t = t xor getCounterAt(i).hashCode()
            h += t
            i++
        }
        if (containsNullKey) h += getCounterAtNull().hashCode()
        return h
    }

    override fun object2ObjectEntrySet(): ObjectSet<Object2ObjectMap.Entry<K, Counter>> {
        return object : AbstractObjectSet<Object2ObjectMap.Entry<K, Counter>>() {
            override fun iterator(): ObjectIterator<Object2ObjectMap.Entry<K, Counter>> {
                return object : ObjectIterator<Object2ObjectMap.Entry<K, Counter>> {
                    var pos = n
                    var last = -1
                    var c = size
                    var mustReturnNull = containsNullKey
                    var currPos = -1
                    override fun hasNext(): Boolean = c != 0
                    override fun next(): Object2ObjectMap.Entry<K, Counter> {
                        if (!hasNext()) throw NoSuchElementException()
                        c--
                        if (mustReturnNull) {
                            mustReturnNull = false
                            last = n
                            currPos = n
                        } else {
                            while (true) {
                                pos--
                                if (key[pos] != null) {
                                    last = pos; currPos = pos; break
                                }
                            }
                        }
                        val idx = currPos
                        return object : Object2ObjectMap.Entry<K, Counter> {
                            override val key: K get() = this@ObjectCounter.key[idx] as K
                            override val value: Counter
                                get() = if (idx == this@ObjectCounter.n) getCounterAtNull() else getCounterAt(
                                    idx
                                )

                            override fun setValue(v: Counter): Counter {
                                val old = value
                                this@ObjectCounter.setCounterAt(idx, v)
                                return old
                            }

                            override fun equals(other: Any?): Boolean {
                                return other is Map.Entry<*, *> && other.key == key && other.value == value
                            }

                            override fun hashCode(): Int = key.hashCode() xor value.hashCode()
                            override fun toString(): String = "$key=>$value"
                        }
                    }

                    override fun remove() {
                        if (last == -1) throw IllegalStateException()
                        if (last == n) {
                            containsNullKey = false
                            key[n] = null
                            lo[n] = 0L
                            hi[n] = 0L
                            bi[n] = null
                        } else {
                            // shiftKeys and adjust pos
                            shiftKeys(last)
                            // adjust pos if needed
                            if (pos >= 0 && key[pos] == null) { /* already */
                            }
                        }
                        this@ObjectCounter.size--
                        last = -1
                    }

                    override fun skip(n: Int): Int {
                        var i = 0
                        while (i < n && hasNext()) {
                            next(); i++
                        }
                        return i
                    }

                    override fun forEachRemaining(action: Consumer<in Object2ObjectMap.Entry<K, Counter>>) {
                        while (hasNext()) action.accept(next())
                    }
                }
            }

            override val size: Int get() = this@ObjectCounter.size
        }
    }

    // High level API
    fun add(k: K, amount: Counter) {
        if (amount.isZero) return
        val pos = find(k)
        if (pos < 0) insert(-pos - 1, k, amount) else {
            val cur = getCounterAt(pos)
            val nxt = cur + amount
            if (nxt.isZero) removeEntry(pos) else setCounterAt(pos, nxt)
        }
    }

    fun add(k: K, amount: Long) {
        if (amount == 0L) return
        add(k, Counter.of(amount))
    }

    fun add(k: K, amount: BigInteger) {
        if (amount.signum() == 0) return
        add(k, Counter.of(amount))
    }

    fun add(k: K, amount: ULong) {
        if (amount == 0UL) return
        add(k, Counter.of(amount))
    }

    fun set(k: K, amount: Counter) {
        if (amount.isZero) remove(k) else {
            val pos = find(k)
            if (pos < 0) insert(-pos - 1, k, amount) else setCounterAt(pos, amount)
        }
    }

    fun set(k: K, amount: BigInteger) {
        if (amount.signum() == 0) remove(k) else {
            val pos = find(k)
            val c = Counter.of(amount)
            if (pos < 0) insert(-pos - 1, k, c) else setCounterAt(pos, c)
        }
    }

    fun set(k: K, amount: Long) {
        if (amount == 0L) remove(k) else {
            val pos = find(k)
            val c = Counter.of(amount)
            if (pos < 0) insert(-pos - 1, k, c) else setCounterAt(pos, c)
        }
    }

    fun getCounter(k: K): Counter {
        val pos = find(k)
        return if (pos < 0) Counter.ZERO else if (pos == n) getCounterAtNull() else getCounterAt(pos)
    }

    fun getBigInteger(k: K): BigInteger = getCounter(k).toBigInteger()
    fun getSaturatedLong(k: K): Long = getCounter(k).longSaturated

    fun removeZeros() {
        val toRemove = mutableListOf<K>()
        for (e in object2ObjectEntrySet()) if (e.value.isZero) toRemove.add(e.key)
        for (k in toRemove) remove(k)
    }

    fun addAll(other: ObjectCounter<K>) {
        for (e in other.object2ObjectEntrySet()) add(e.key, e.value)
    }

    fun addAll(other: KeyCounter) {
        for (entry in other) {
            @Suppress("UNCHECKED_CAST") add(entry.key as K, entry.longValue)
        }
    }

    fun collectChangedKeys(other: ObjectCounter<K>, out: Consumer<K>) {
        for (e in object2ObjectEntrySet()) if (e.value != other.getCounter(e.key)) out.accept(e.key)
        for (e in other.object2ObjectEntrySet()) if (!containsKey(e.key)) out.accept(e.key)
    }

    fun copy(): ObjectCounter<K> {
        val c = ObjectCounter<K>(size, f)
        for ((key, value) in object2ObjectEntrySet()) c[key] = value
        return c
    }

    fun copySaturatedTo(out: KeyCounter) {
        for (e in object2ObjectEntrySet()) {
            @Suppress("UNCHECKED_CAST") out.add(e.key as AEKey, e.value.longSaturated)
        }
    }

    companion object {
        @JvmStatic
        fun fromKeyCounter(counter: KeyCounter?): BigKeyCounter? {
            if (counter == null) return null
            return BigKeyCounter().also { it.addAll(counter) }
        }
    }
}
