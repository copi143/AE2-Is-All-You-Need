package allyouneed.pattern.term

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EncodingKindTest {
    @Test
    fun namesAreStableForNbt() {
        assertEquals("MACHINE", EncodingKind.MACHINE.name)
        assertEquals("PROCESSING", EncodingKind.PROCESSING.name)
        assertEquals("PROBABILITY", EncodingKind.PROBABILITY.name)
        assertEquals("PSEUDO", EncodingKind.PSEUDO.name)
    }

    @Test
    fun byNameFallsBackToMachine() {
        assertEquals(EncodingKind.PROBABILITY, EncodingKind.byName("PROBABILITY"))
        assertEquals(EncodingKind.MACHINE, EncodingKind.byName("nope"))
    }
}
