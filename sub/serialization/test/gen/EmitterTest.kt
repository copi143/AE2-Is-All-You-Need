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
                SerialProp("u", "u", null, "u", SerialTy.U32, false),
                SerialProp("v", "v", null, "v", SerialTy.VarU64, false),
            ),
        )
        val out = emit(cls).toString()
        assertContains(out, "tag.putInt(\"u\", u.toInt())")
        assertContains(out, "tag.getInt(\"u\").toUInt()")
        assertContains(out, "buf.writeVarLong(v.toLong())")
        assertContains(out, "buf.readVarLong().toULong()")
    }
}
