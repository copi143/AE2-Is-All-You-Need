package allyouneed.core

import appeng.api.stacks.KeyInterner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class KeyInternerTest {
    @AfterEach
    fun tearDown() {
        KeyInterner.clear()
    }

    @Test
    fun `equal instances collapse to identity`() {
        val a = SampleKey("diamond", 1)
        val b = SampleKey("diamond", 1)
        assertEquals(a, b)
        assertNotEquals(System.identityHashCode(a), System.identityHashCode(b))
        assertSame(KeyInterner.intern(a), KeyInterner.intern(b))
        assertEquals(1, KeyInterner.size())
    }

    @Test
    fun `different content stays distinct`() {
        val a = KeyInterner.intern(SampleKey("iron"))
        val b = KeyInterner.intern(SampleKey("gold"))
        assertNotEquals(a, b)
        assertEquals(2, KeyInterner.size())
    }

    @Test
    fun `constructed instances collapse after intern`() {
        val a = KeyInterner.intern(SampleKey("copper", 3))
        val b = KeyInterner.intern(SampleKey("copper", 3))
        assertSame(a, b)
        a as SampleKey
        assertEquals("copper", a.name)
        assertEquals(3, a.extra)
    }
}
