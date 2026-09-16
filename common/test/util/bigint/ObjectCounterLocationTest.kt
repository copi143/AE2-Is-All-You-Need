package allyouneed.util.bigint

import allyouneed.api.KeyLocation
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectCounterLocationTest {

    @Test
    fun `copy and addAll keep locations`() {
        val src = ObjectCounter<String>()
        val cell = Any()
        src.add("iron", 10L)
        src.addLocation("iron", cell, BigInteger.TEN)
        val copy = src.copy()
        assertEquals(cell, copy.getLocations("iron").single().storage)
        val dest = ObjectCounter<String>()
        dest.addAll(src)
        assertEquals(cell, dest.getLocations("iron").single().storage)
    }

    @Test
    fun `clear and remove drop locations`() {
        val oc = ObjectCounter<String>()
        val cell = Any()
        oc.add("iron", 10L)
        oc.addLocation("iron", cell, BigInteger.TEN)
        oc.remove("iron")
        assertTrue(oc.getLocations("iron").isEmpty())
        oc.add("iron", 4L)
        oc.addLocation("iron", cell, BigInteger.valueOf(4))
        oc.clear()
        assertTrue(oc.getLocations("iron").isEmpty())
    }

    @Test
    fun `setLocations replaces one key`() {
        val oc = ObjectCounter<String>()
        val old = Any()
        val next = Any()
        oc.addLocation("iron", old, BigInteger.TEN)
        oc.setLocations("iron", listOf(KeyLocation(next, BigInteger.TWO)))
        assertEquals(next, oc.getLocations("iron").single().storage)
        oc.setLocations("iron", emptyList())
        assertTrue(oc.getLocations("iron").isEmpty())
    }
}
