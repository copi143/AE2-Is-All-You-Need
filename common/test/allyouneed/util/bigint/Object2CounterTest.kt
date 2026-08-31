package allyouneed.util.bigint

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [Object2Counter.hashCode] 的回归测试（BigStack / BigIngredient 等子类共用）。
 *
 * 已覆盖 bug：`(this as Counter).hashCode()` 因虚拟分派回调本方法造成无限递归栈溢出，
 * 同时 [Object2Counter.key] 泛型可带空值导致 `key.hashCode()` 空指针。
 */
class Object2CounterTest {

    @Test
    fun `hashCode is stable for equal instances`() {
        val a = Object2Counter("a", 1L)
        val b = Object2Counter("a", 1L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hashCode tolerates null key`() {
        val a = Object2Counter<String?>(null, 1L)
        val b = Object2Counter<String?>(null, 1L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hashCode differs for different amounts`() {
        val small = Object2Counter("a", 1L)
        val big = Object2Counter("a", 2L)
        assertNotEquals(small, big)
    }
}