package io.github.copi143.serialization.gen

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class RoundTripTest {
    @Test
    fun mapStringOptimizedGeneratesCompound() {
        val cls = SerialClass(
            pkg = "serialization",
            name = "WithMaps",
            construct = true,
            fields = listOf(
                SerialProp("strInt", "strInt", null, "strInt", SerialTy.MapOf(SerialTy.Str, SerialTy.I32), false),
                SerialProp("uuidStr", "uuidStr", null, "uuidStr", SerialTy.MapOf(SerialTy.UUID, SerialTy.Str), false),
            ),
        )
        val out = emit(cls).toString()
        // Map<String, Int> should use CompoundTag path: mapTag.putInt(k, v) and tag.getCompound
        assertContains(out, "mapTag.putInt(k, v)")
        assertContains(out, "tag.getCompound(\"strInt\")")
        assertContains(out, "_strInt.allKeys")
        // Map<UUID, String> should use ListTag path: entry.putUUID / putString
        assertContains(out, "entry.putUUID(\"k\", k)")
        assertContains(out, "tag.getList(\"uuidStr\"")
        assertTrue("m.allKeys" !in out.substringAfter("uuidStr"))
    }

    @Test
    fun setGeneratesListTag() {
        val cls = SerialClass(
            pkg = "serialization",
            name = "WithSets",
            construct = true,
            fields = listOf(
                SerialProp("strSet", "strSet", null, "strSet", SerialTy.SetOf(SerialTy.Str), false),
            ),
        )
        val out = emit(cls).toString()
        assertContains(out, "LinkedHashSet")
        assertContains(out, "Tag.TAG_STRING")
    }

    @Test
    fun nullableMapGeneratesNullCheck() {
        val cls = SerialClass(
            pkg = "serialization",
            name = "WithNullable",
            construct = true,
            fields = listOf(
                SerialProp("id", "id", null, "id", SerialTy.I32, false),
                SerialProp("optMap", "optMap", null, "optMap", SerialTy.MapOf(SerialTy.Str, SerialTy.Str), true),
            ),
        )
        val out = emit(cls).toString()
        assertContains(out, "if (tag.contains(\"optMap\"))")
        assertContains(out, "else null")
        assertContains(out, "LinkedHashMap")
    }

    @Test
    fun varLenUsesVarInt() {
        val cls = SerialClass(
            pkg = "serialization",
            name = "WithVarLen",
            construct = true,
            fields = listOf(
                SerialProp("vi", "vi", null, "vi", SerialTy.VarI32, false),
                SerialProp("vu", "vu", null, "vu", SerialTy.VarU32, false),
            ),
        )
        val out = emit(cls).toString()
        assertContains(out, "writeVarInt(vi)")
        assertContains(out, "readVarInt().toUInt()")
        // NBT still uses fixed
        assertContains(out, "tag.putInt(\"vi\"")
    }

    @Test
    fun unsignedUsesCorrectPut() {
        val cls = SerialClass(
            pkg = "serialization",
            name = "WithUnsigned",
            construct = true,
            fields = listOf(
                SerialProp("ub", "ub", null, "ub", SerialTy.U8, false),
                SerialProp("ui", "ui", null, "ui", SerialTy.U32, false),
            ),
        )
        val out = emit(cls).toString()
        assertContains(out, "putByte(\"ub\", ub.toByte())")
        assertContains(out, "getByte(\"ub\").toUByte()")
        assertContains(out, "putInt(\"ui\", ui.toInt())")
    }

    @Test
    fun stabilityNbtAndBufIdempotent() {
        // verify that generated code for same input is deterministic and contains expected structure
        val cls = SerialClass(
            pkg = "serialization",
            name = "Stability",
            construct = true,
            fields = listOf(
                SerialProp("a", "a", null, "a", SerialTy.I32, false),
                SerialProp("b", "b", null, "b", SerialTy.ListOf(SerialTy.Str), false),
                SerialProp("c", "c", null, "c", SerialTy.MapOf(SerialTy.Str, SerialTy.I32), false),
            ),
        )
        val out1 = emit(cls).toString()
        val out2 = emit(cls).toString()
        assertTrue(out1 == out2, "emit should be deterministic")
        assertContains(out1, "fun Stability.toNbt()")
        assertContains(out1, "fun Stability.write(")
        assertContains(out1, "fun Stability.Companion.fromNbt")
        assertContains(out1, "fun Stability.Companion.read")
    }
}
