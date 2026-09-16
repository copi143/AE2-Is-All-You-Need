package io.github.copi143.serialization.gen

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class EmitterTest {
    @Test
    fun emitsNbtAndPacket() {
        val cls = SerialClass(
            pkg = "allyouneed.parts.logger",
            name = "NetworkLogEntry",
            construct = true,
            fields = listOf(
                SerialProp("utcMillis", "utcMillis", null, "t", SerialTy.I64, false),
                SerialProp("kind", "kind", null, "k", SerialTy.Enum("NetworkLogKind", true), false),
                SerialProp("args", "args", null, "a", SerialTy.ListOf(SerialTy.Str), false),
            ),
        )
        val out = emit(cls)
        assertContains(out, "fun NetworkLogEntry.toNbt()")
        assertContains(out, "fun NetworkLogEntry.Companion.fromNbt")
        assertContains(out, "tag.putLong(\"t\", this.utcMillis)")
        assertContains(out, "tag.putByte(\"k\", this.kind.ordinal.toByte())")
        assertTrue("MethodHandle" !in out)
    }
}
