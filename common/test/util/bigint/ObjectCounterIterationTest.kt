package allyouneed.util.bigint

import org.junit.jupiter.api.Test
import java.util.NoSuchElementException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ObjectCounter] EntrySet 迭代器 remove() 的回归测试。
 *
 * 已覆盖 bug：remove() 只递减 size，未同步迭代器自身计数/扫描位置，且 shiftKeys 会左移簇内条目，
 * 导致后续 next() 漏读/重复，键耗尽时索引越过 0 越界或返回错误集合。
 */
class ObjectCounterIterationTest {

    @Test
    fun `remove in the middle leaves all other entries intact`() {
        val oc = ObjectCounter<String>()
        for (c in 'a'..'h') oc.add(c.toString(), 1L)
        val seen = mutableListOf<String>()
        val it = oc.object2ObjectEntrySet().iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key == "d") it.remove()
            seen += e.key
        }
        assertEquals(('a'..'h').map { it.toString() }.toSet(), seen.toSet())
        assertEquals(8, seen.size) // 每个键恰好遍历一次，无丢无重
        assertEquals(7, oc.size)
        assertFalse(oc.containsKey("d")) // 被 remove 的键已物理移除
    }

    @Test
    fun `remove every element during iteration empties the counter`() {
        val oc = ObjectCounter<String>()
        for (c in 'a'..'c') oc.add(c.toString(), 1L)
        val it = oc.object2ObjectEntrySet().iterator()
        var removed = 0
        while (it.hasNext()) {
            it.next()
            it.remove()
            removed++
        }
        assertEquals(3, removed)
        assertEquals(0, oc.size)
        assertTrue(oc.isEmpty())
    }

    @Test
    fun `remove null key during iteration`() {
        val oc = ObjectCounter<String?>()
        oc.add(null, 5L)
        oc.add("a", 1L)
        val seen = mutableListOf<String?>()
        val it = oc.object2ObjectEntrySet().iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key == null) it.remove()
            seen += e.key
        }
        assertEquals(2, seen.size) // a 与 null 各恰好出现一次
        assertTrue(seen.contains("a"))
        assertTrue(seen.contains(null))
        assertEquals(1, oc.size)
        assertEquals(1L, oc.get("a")?.longSaturated)
        assertFalse(oc.containsKey(null)) // null 键已被移除
    }

    @Test
    fun `remove without next throws`() {
        val oc = ObjectCounter<String>()
        oc.add("a", 1L)
        val it = oc.object2ObjectEntrySet().iterator()
        assertFailsWith<IllegalStateException> { it.remove() }
    }

    @Test
    fun `double remove throws`() {
        val oc = ObjectCounter<String>()
        oc.add("a", 1L)
        val it = oc.object2ObjectEntrySet().iterator()
        it.next()
        it.remove()
        assertFailsWith<IllegalStateException> { it.remove() }
    }

    @Test
    fun `next after full iteration throws`() {
        val oc = ObjectCounter<String>()
        oc.add("a", 1L)
        val it = oc.object2ObjectEntrySet().iterator()
        while (it.hasNext()) it.next()
        assertFailsWith<NoSuchElementException> { it.next() }
    }
}