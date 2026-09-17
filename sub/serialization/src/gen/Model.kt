package io.github.copi143.serialization.gen

data class SerialClass(
    val pkg: String,
    val name: String,
    val fields: List<SerialProp>,
    val construct: Boolean,
)

data class SerialProp(
    val name: String,
    val getter: String,
    val setter: String?,
    val wireName: String,
    val type: SerialTy,
    val nullable: Boolean,
    val varLen: Boolean = false,
)

sealed class SerialTy(
    val type: String,
    val shortType: String,
    val types: Set<String>,
    open val nbt: Pair<String, String>,
    open val buf: Pair<String, String>,
) {
    constructor(
        types: List<String>,
        nbt: Pair<String, String>,
        buf: Pair<String, String>,
    ) : this(
        types.first().removePrefix("#"),
        types.first().removePrefix("#").removePrefix("kotlin.").removePrefix("java.lang."),
        types.filter { !it.startsWith("#") }.toSet(),
        nbt,
        buf,
    )

    constructor(
        type: String,
        nbt: Pair<String, String>,
        buf: Pair<String, String>,
    ) : this(listOf(type), nbt, buf)

    constructor(type: String, java: Boolean = true) : this(
        if ('.' !in type) {
            if (java) listOf("kotlin.$type", "java.lang.$type") else listOf("kotlin.$type")
        } else {
            listOf(type)
        },
        if ('.' !in type) "get$type" to "put$type" else dummy,
        if ('.' !in type) "read$type" to "write$type" else dummy,
    )

    val nbtGet: String get() = nbt.first
    val nbtSet: String get() = nbt.second
    val bufGet: String get() = buf.first
    val bufSet: String get() = buf.second

    sealed class Number(val bits: Int, val unsigned: Boolean, val varLen: Boolean = false) : SerialTy(
        types(bits, unsigned).map { if (varLen) "#$it" else it },
        "get${suffix(bits)}" to "put${suffix(bits)}",
        if (varLen) {
            "readVar${suffix(bits)}" to "writeVar${suffix(bits)}"
        } else {
            "read${suffix(bits)}" to "write${suffix(bits)}"
        },
    ) {
        companion object {
            fun suffix(bits: Int) = when (bits) {
                8 -> "Byte"
                16 -> "Short"
                32 -> "Int"
                64 -> "Long"
                else -> throw IllegalArgumentException()
            }

            fun types(bits: Int, unsigned: Boolean): List<String> {
                val suffix = suffix(bits)
                return if (unsigned) {
                    listOf("kotlin.U$suffix")
                } else {
                    listOf("kotlin.$suffix", "java.lang.$suffix")
                }
            }
        }
    }

    data object Bool : SerialTy("Boolean")
    data object F32 : SerialTy("Float")
    data object F64 : SerialTy("Double")
    data object Str : SerialTy("String") {
        override val buf = "readUtf" to "writeUtf"
    }

    data object I8 : Number(8, false)
    data object I16 : Number(16, false)
    data object I32 : Number(32, false)
    data object I64 : Number(64, false)
    data object VarI32 : Number(32, false, varLen = true)
    data object VarI64 : Number(64, false, varLen = true)

    data object U8 : Number(8, true)
    data object U16 : Number(16, true)
    data object U32 : Number(32, true)
    data object U64 : Number(64, true)
    data object VarU32 : Number(32, true, varLen = true)
    data object VarU64 : Number(64, true, varLen = true)

    data object BigInt : SerialTy("java.math.BigInteger", nbtByteArray, bufByteArray)
    data object VarBigInt : SerialTy("#java.math.BigInteger", nbtByteArray, bufByteArray)

    data object I8Array : SerialTy("ByteArray", false)
    data object I32Array : SerialTy("IntArray", false)
    data object I64Array : SerialTy("LongArray", false)

    data object UUID : SerialTy("java.util.UUID") {
        override val nbt = "getUUID" to "putUUID"
        override val buf = "readUUID" to "writeUUID"
    }

    data object ResLoc : SerialTy("net.minecraft.resources.ResourceLocation") {
        override val nbt = Str.nbt
        override val buf = Str.buf
    }

    data object BlockPos : SerialTy("net.minecraft.core.BlockPos") {
        override val nbt = I64.nbt
        override val buf = I64.buf
    }

    data class Enum(val name: String, val ordinal: Boolean) : SerialTy(
        name,
        if (ordinal) I8.nbt else Str.nbt,
        if (ordinal) I8.buf else Str.buf,
    )

    sealed class Container(type: String, shortType: String) : SerialTy(
        type, shortType, setOf(type), dummy, dummy,
    )

    data class ListOf(val element: SerialTy) : Container(
        "kotlin.collections.List<${element.type}>",
        "List<${element.shortType}>",
    )

    data class MapOf(val key: SerialTy, val value: SerialTy) : Container(
        "kotlin.collections.Map<${key.type}, ${value.type}>",
        "Map<${key.shortType}, ${value.shortType}>",
    )

    data class SetOf(val element: SerialTy) : Container(
        "kotlin.collections.Set<${element.type}>",
        "Set<${element.shortType}>",
    )

    data class Nested(val name: String) : SerialTy(name, name, setOf(name), "" to "", "" to "")

    companion object {
        private val nbtByteArray = "getByteArray" to "putByteArray"
        private val bufByteArray = "readByteArray" to "writeByteArray"
        private val dummy = "" to ""

        private val primitives: List<SerialTy> = listOf(
            Bool, F32, F64, Str,
            I8, I16, I32, I64, VarI32, VarI64,
            U8, U16, U32, U64, VarU32, VarU64,
            BigInt, VarBigInt,
            I8Array, I32Array, I64Array,
            UUID, ResLoc, BlockPos,
        )

        private val byName: Map<String, SerialTy> by lazy {
            primitives.flatMap { type -> type.types.map { name -> name to type } }.toMap()
        }

        fun primitive(qn: String, varLen: Boolean = false): SerialTy? {
            val base = byName[qn] ?: return null
            return if (!varLen) base else when (base) {
                I32 -> VarI32
                I64 -> VarI64
                U32 -> VarU32
                U64 -> VarU64
                BigInt -> VarBigInt
                else -> base
            }
        }
    }
}
