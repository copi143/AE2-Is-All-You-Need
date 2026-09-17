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

sealed class SerialTy {
    data object Bool : SerialTy()
    data object I8 : SerialTy()
    data object I16 : SerialTy()
    data object I32 : SerialTy()
    data object F32 : SerialTy()
    data object F64 : SerialTy()
    data object I64 : SerialTy()
    data object U8 : SerialTy()
    data object U16 : SerialTy()
    data object U32 : SerialTy()
    data object U64 : SerialTy()
    data object VarU32 : SerialTy()
    data object VarU64 : SerialTy()
    data object Str : SerialTy()
    data object Bytes : SerialTy()
    data object Ints : SerialTy()
    data object Longs : SerialTy()
    data object BigInt : SerialTy()
    data object Uuid : SerialTy()
    data object ResLoc : SerialTy()
    data object BlockPos : SerialTy()
    data object VarInt : SerialTy()
    data object VarLong : SerialTy()
    data object VarBigInt : SerialTy()
    data class Enum(val name: String, val ordinal: Boolean) : SerialTy()
    data class ListOf(val element: SerialTy) : SerialTy()
    data class MapOf(val key: SerialTy, val value: SerialTy) : SerialTy()
    data class SetOf(val element: SerialTy) : SerialTy()
    data class Nested(val name: String) : SerialTy()
}
