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
                SerialProp("utcMillis", "utcMillis", null, "t", SerialType.I64, false),
                SerialProp("kind", "kind", null, "k", SerialType.Enum("NetworkLogKind", true), false),
                SerialProp("args", "args", null, "a", SerialType.ListOf(SerialType.Str), false),
            ),
        )
        val out = emit(cls).toString()
        assertContains(out, "import net.minecraft.nbt.CompoundTag")
        assertContains(out, "import net.minecraft.network.FriendlyByteBuf")
        assertContains(out, "fun NetworkLogEntry.toNbt()")
        assertContains(out, "fun NetworkLogEntry.Companion.fromNbt")
        assertContains(out, "tag.putLong(\"t\", utcMillis)")
        assertContains(out, "tag.putByte(\"k\", kind.ordinal.toByte())")
        assertTrue("MethodHandle" !in out)
    }

    @Test
    fun emitsUnsigned() {
        val cls = SerialClass(
            pkg = "t",
            name = "Ids",
            construct = true,
            fields = listOf(
                SerialProp("u", "u", null, "u", SerialType.U32, false),
                SerialProp("v", "v", null, "v", SerialType.VarU64, false),
            ),
        )
        val out = emit(cls).toString()
        assertContains(out, "tag.putInt(\"u\", u.toInt())")
        assertContains(out, "tag.getInt(\"u\").toUInt()")
        assertContains(out, "buf.writeVarLong(v.toLong())")
        assertContains(out, "buf.readVarLong().toULong()")
    }
}
