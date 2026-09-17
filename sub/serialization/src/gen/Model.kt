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

sealed class SerialTy(vararg types: String) {
    open val type: String = types.firstOrNull() ?: ""
    open val shortType: String = types.firstOrNull()?.removePrefix("kotlin.")?.removePrefix("java.lang.") ?: ""
    open val types: Set<String> = types.toSet()

    data object Bool : SerialTy("kotlin.Boolean", "java.lang.Boolean")

    data object I8 : SerialTy("kotlin.Byte", "java.lang.Byte")
    data object I16 : SerialTy("kotlin.Short", "java.lang.Short")
    data object I32 : SerialTy("kotlin.Int", "java.lang.Integer")
    data object I64 : SerialTy("kotlin.Long", "java.lang.Long")
    data object VarI32 : SerialTy()
    data object VarI64 : SerialTy()

    data object U8 : SerialTy("kotlin.UByte")
    data object U16 : SerialTy("kotlin.UShort")
    data object U32 : SerialTy("kotlin.UInt")
    data object U64 : SerialTy("kotlin.ULong")
    data object VarU32 : SerialTy()
    data object VarU64 : SerialTy()

    data object BigInt : SerialTy("java.math.BigInteger")
    data object VarBigInt : SerialTy()

    data object F32 : SerialTy("kotlin.Float", "java.lang.Float")
    data object F64 : SerialTy("kotlin.Double", "java.lang.Double")

    data object Str : SerialTy("kotlin.String", "java.lang.String")
    data object I8Array : SerialTy("kotlin.ByteArray")
    data object I32Array : SerialTy("kotlin.IntArray")
    data object I64Array : SerialTy("kotlin.LongArray")
    data object UUID : SerialTy("java.util.UUID")
    data object ResLoc : SerialTy("net.minecraft.resources.ResourceLocation")
    data object BlockPos : SerialTy("net.minecraft.core.BlockPos")

    data class Enum(val name: String, val ordinal: Boolean) : SerialTy() {
        override val type: String = name
        override val shortType: String = name
        override val types: Set<String> = setOf(type)
    }

    data class ListOf(val element: SerialTy) : SerialTy() {
        override val type: String = "kotlin.collections.List<${element.type}>"
        override val shortType: String = "List<${element.shortType}>"
        override val types: Set<String> = setOf(type)
    }

    data class MapOf(val key: SerialTy, val value: SerialTy) : SerialTy() {
        override val type: String = "kotlin.collections.Map<${key.type}, ${value.type}>"
        override val shortType: String = "Map<${key.shortType}, ${value.shortType}>"
        override val types: Set<String> = setOf(type)
    }

    data class SetOf(val element: SerialTy) : SerialTy() {
        override val type: String = "kotlin.collections.Set<${element.type}>"
        override val shortType: String = "Set<${element.shortType}>"
        override val types: Set<String> = setOf(type)
    }

    data class Nested(val name: String) : SerialTy() {
        override val type: String = name
        override val shortType: String = name
        override val types: Set<String> = setOf(type)
    }

    companion object {
        private val primitives: List<SerialTy> = listOf(
            Bool,
            I8, I16, I32, I64, VarI32, VarI64,
            U8, U16, U32, U64, VarU32, VarU64,
            BigInt, VarBigInt,
            F32, F64,
            Str,
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
