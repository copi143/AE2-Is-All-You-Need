package allyouneed.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `content identity intern ignores public identity equals`() {
        val a = IdentityKey("same")
        val b = IdentityKey("same")
        assertFalse(a.equals(b))
        assertTrue(a.`asm$equals`(b))
        assertSame(KeyInterner.intern(a), KeyInterner.intern(b))
        assertEquals(1, KeyInterner.size())
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

    private class IdentityKey(val name: String) : ContentIdentity {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
        override fun `asm$equals`(other: Any?): Boolean = other is IdentityKey && name == other.name
        override fun `asm$hashCode`(): Int = name.hashCode()
    }
}
