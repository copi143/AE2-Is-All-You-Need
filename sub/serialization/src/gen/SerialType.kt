package io.github.copi143.serialization.gen

import com.squareup.kotlinpoet.CodeBlock

// File-level so object construction during class init never touches a half-built companion.
private val nbtByteArray = "getByteArray" to "putByteArray"
private val bufByteArray = "readByteArray" to "writeByteArray"
private val dummy = "" to ""

private fun numSuffix(bits: Int) = when (bits) {
    8 -> "Byte"
    16 -> "Short"
    32 -> "Int"
    64 -> "Long"
    else -> throw IllegalArgumentException()
}

private fun numTypes(bits: Int, unsigned: Boolean): List<String> {
    val suffix = numSuffix(bits)
    return if (unsigned) {
        listOf("kotlin.U$suffix")
    } else {
        listOf("kotlin.$suffix", "java.lang.$suffix")
    }
}

sealed class SerialType(
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

    open val imports: Set<Pair<String, String>> get() = emptySet()

    abstract fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean)
    abstract fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock
    abstract fun emitBufWrite(b: CodeBlock.Builder, value: String)
    abstract fun bufRead(): CodeBlock
    abstract fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String)
    abstract fun listTag(): CodeBlock
    abstract fun listGet(listVar: String): CodeBlock

    protected fun putNbt(
        b: CodeBlock.Builder,
        tagVar: String,
        setter: String,
        key: String,
        arg: String,
        keyIsVar: Boolean,
    ) {
        if (keyIsVar) b.addStatement("%L.%L(%L, %L)", tagVar, setter, key, arg)
        else b.addStatement("%L.%L(%S, %L)", tagVar, setter, key, arg)
    }

    protected fun getNbt(
        tagVar: String, getter: String, key: String, keyIsVar: Boolean, suffix: String = ""
    ): CodeBlock = if (keyIsVar) CodeBlock.of("%L.%L(%L)%L", tagVar, getter, key, suffix)
    else CodeBlock.of("%L.%L(%S)%L", tagVar, getter, key, suffix)

    sealed class Number(val bits: Int, val unsigned: Boolean, val varLen: Boolean = false) : SerialType(
        numTypes(bits, unsigned).map { if (varLen) "#$it" else it },
        "get${numSuffix(bits)}" to "put${numSuffix(bits)}",
        if (varLen) {
            "readVar${numSuffix(bits)}" to "writeVar${numSuffix(bits)}"
        } else {
            "read${numSuffix(bits)}" to "write${numSuffix(bits)}"
        },
    ) {
        private fun nbtArg(v: String): String = when {
            !unsigned -> v
            else -> when (bits) {
                8 -> "$v.toByte()"
                16 -> "$v.toShort()"
                32 -> "$v.toInt()"
                64 -> "$v.toLong()"
                else -> v
            }
        }

        private fun wrapSuffix(): String = when {
            !unsigned -> ""
            else -> when (bits) {
                8 -> ".toUByte()"
                16 -> ".toUShort()"
                32 -> ".toUInt()"
                64 -> ".toULong()"
                else -> ""
            }
        }

        private fun bufArg(v: String): String = when (bits) {
            8, 16 -> "$v.toInt()"
            32 -> if (unsigned) "$v.toInt()" else v
            64 -> if (unsigned) "$v.toLong()" else v
            else -> v
        }

        val tagClass = when (bits) {
            8 -> Clazz.ByteTag
            16 -> Clazz.ShortTag
            32 -> Clazz.IntTag
            64 -> Clazz.LongTag
            else -> error("bad bits $bits")
        }

        val tagId = when (bits) {
            8 -> "TAG_BYTE"
            16 -> "TAG_SHORT"
            32 -> "TAG_INT"
            64 -> "TAG_LONG"
            else -> error("bad bits $bits")
        }

        val numericGetter = when (bits) {
            8 -> "getAsByte"
            16 -> "getAsShort"
            32 -> "getAsInt"
            64 -> "getAsLong"
            else -> error("bad bits $bits")
        }

        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, nbtArg(value), keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar, wrapSuffix())

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L)", buf.second, bufArg(value))
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("buf.%L()%L", buf.first, wrapSuffix())

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L))", listVar, tagClass, nbtArg(itemVar))
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.%L.toInt()", Clazz.Tag, tagId)

        override fun listGet(listVar: String): CodeBlock =
            CodeBlock.of("(%L.get(i) as %T).%L()%L", listVar, Clazz.NumericTag, numericGetter, wrapSuffix())

    }

    data object Bool : SerialType("Boolean") {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L)", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("buf.%L()", buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L))", listVar, Clazz.ByteTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_BYTE.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock =
            CodeBlock.of("(%L.get(i) as %T).getAsByte().toInt() != 0", listVar, Clazz.NumericTag)
    }

    data object F32 : SerialType("Float") {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L)", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("buf.%L()", buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object F64 : SerialType("Double") {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L)", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("buf.%L()", buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object Str : SerialType("String") {
        override val buf = "readUtf" to "writeUtf"

        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L)", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("buf.%L()", buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_STRING.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = CodeBlock.of("%L.getString(i)", listVar)
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

    data object BigInt : SerialType("java.math.BigInteger", nbtByteArray, bufByteArray) {
        override val imports = setOf("java.math" to "BigInteger")

        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, "$value.toByteArray()", keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            if (keyIsVar) CodeBlock.of("%T(%L.%L(%L))", Clazz.BigInteger, tagVar, nbt.first, key)
            else CodeBlock.of("%T(%L.%L(%S))", Clazz.BigInteger, tagVar, nbt.first, key)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L.toByteArray())", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("%T(buf.%L())", Clazz.BigInteger, buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object VarBigInt : SerialType("#java.math.BigInteger", nbtByteArray, bufByteArray) {
        override val imports = setOf("java.math" to "BigInteger")

        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            BigInt.emitNbtPut(b, tagVar, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            BigInt.nbtGet(tagVar, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) = BigInt.emitBufWrite(b, value)

        override fun bufRead(): CodeBlock = BigInt.bufRead()

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) =
            BigInt.emitListAdd(b, listVar, itemVar)

        override fun listTag(): CodeBlock = BigInt.listTag()

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object I8Array : SerialType("ByteArray", false) {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L)", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("buf.%L()", buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object I32Array : SerialType("IntArray", false) {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.writeVarInt(%L.size)", value)
            b.addStatement("for (n in %L) buf.writeInt(n)", value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("IntArray(buf.readVarInt()) { buf.readInt() }")

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object I64Array : SerialType("LongArray", false) {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.writeVarInt(%L.size)", value)
            b.addStatement("for (n in %L) buf.writeLong(n)", value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("LongArray(buf.readVarInt()) { buf.readLong() }")

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object UUID : SerialType(
        "java.util.UUID",
        "getUUID" to "putUUID",
        "readUUID" to "writeUUID",
    ) {
        override val imports = setOf("java.util" to "UUID")

        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, value, keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            getNbt(tagVar, nbt.first, key, keyIsVar)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L)", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("buf.%L()", buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object ResLoc : SerialType(
        "net.minecraft.resources.ResourceLocation",
        "getString" to "putString",
        "readUtf" to "writeUtf",
    ) {
        override val imports = setOf("net.minecraft.resources" to "ResourceLocation")

        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, "$value.toString()", keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            if (keyIsVar) CodeBlock.of("%T(%L.%L(%L))", Clazz.ResourceLocation, tagVar, nbt.first, key)
            else CodeBlock.of("%T(%L.%L(%S))", Clazz.ResourceLocation, tagVar, nbt.first, key)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L.toString())", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("%T(buf.%L())", Clazz.ResourceLocation, buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data object BlockPos : SerialType(
        "net.minecraft.core.BlockPos",
        "getLong" to "putLong",
        "readLong" to "writeLong",
    ) {
        override val imports = setOf("net.minecraft.core" to "BlockPos")

        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            putNbt(b, tagVar, nbt.second, key, "$value.asLong()", keyIsVar)

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            if (keyIsVar) CodeBlock.of("%T.of(%L.%L(%L))", Clazz.BlockPos, tagVar, nbt.first, key)
            else CodeBlock.of("%T.of(%L.%L(%S))", Clazz.BlockPos, tagVar, nbt.first, key)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("buf.%L(%L.asLong())", buf.second, value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("%T.of(buf.%L())", Clazz.BlockPos, buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data class Enum(val name: String, val ordinal: Boolean) : SerialType(
        name,
        if (ordinal) "getByte" to "putByte" else "getString" to "putString",
        if (ordinal) "readByte" to "writeByte" else "readUtf" to "writeUtf",
    ) {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) {
            if (ordinal) putNbt(b, tagVar, nbt.second, key, "$value.ordinal.toByte()", keyIsVar)
            else putNbt(b, tagVar, nbt.second, key, "$value.name", keyIsVar)
        }

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock = if (ordinal) {
            if (keyIsVar) CodeBlock.of("%L.entries[%L.%L(%L).toInt() and 0xFF]", name, tagVar, nbt.first, key)
            else CodeBlock.of("%L.entries[%L.%L(%S).toInt() and 0xFF]", name, tagVar, nbt.first, key)
        } else {
            if (keyIsVar) CodeBlock.of("%L.valueOf(%L.%L(%L))", name, tagVar, nbt.first, key)
            else CodeBlock.of("%L.valueOf(%L.%L(%S))", name, tagVar, nbt.first, key)
        }

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            if (ordinal) b.addStatement("buf.%L(%L.ordinal)", buf.second, value)
            else b.addStatement("buf.%L(%L.name)", buf.second, value)
        }

        override fun bufRead(): CodeBlock =
            if (ordinal) CodeBlock.of("%L.entries[buf.readUnsignedByte().toInt()]", name)
            else CodeBlock.of("%L.valueOf(buf.%L())", name, buf.first)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            if (ordinal) b.addStatement(
                "%L.add(%T.valueOf(%L.ordinal.toByte()))",
                listVar, Clazz.ByteTag, itemVar,
            )
            else b.addStatement("%L.add(%T.valueOf(%L.name))", listVar, Clazz.StringTag, itemVar)
        }

        override fun listTag(): CodeBlock = if (ordinal) CodeBlock.of("%T.TAG_BYTE.toInt()", Clazz.Tag)
        else CodeBlock.of("%T.TAG_STRING.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = if (ordinal) CodeBlock.of(
            "%L.entries[(%L.get(i) as %T).getAsByte().toInt() and 0xFF]",
            name, listVar, Clazz.NumericTag,
        )
        else CodeBlock.of("%L.valueOf(%L.getString(i))", name, listVar)
    }

    sealed class Container(type: String, shortType: String) : SerialType(
        type, shortType, setOf(type), dummy, dummy,
    ) {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) =
            throw IllegalStateException()

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock = throw IllegalStateException()
        override fun emitBufWrite(b: CodeBlock.Builder, value: String) = throw IllegalStateException()
        override fun bufRead(): CodeBlock = throw IllegalStateException()
        override fun listGet(listVar: String): CodeBlock = throw IllegalStateException()
    }

    data class ListOf(val element: SerialType) : Container(
        "kotlin.collections.List<${element.type}>",
        "List<${element.shortType}>",
    ) {
        override val imports get() = element.imports

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("val __inner = %T()", Clazz.ListTag)
            b.beginControlFlow("for (__e in %L)", itemVar)
            element.emitListAdd(b, "__inner", "__e")
            b.endControlFlow()
            b.addStatement("%L.add(__inner)", listVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)
    }

    data class MapOf(val key: SerialType, val value: SerialType) : Container(
        "kotlin.collections.Map<${key.type}, ${value.type}>",
        "Map<${key.shortType}, ${value.shortType}>",
    ) {
        override val imports get() = key.imports + value.imports

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%L.toNbtTag())", listVar, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)
    }

    data class SetOf(val element: SerialType) : Container(
        "kotlin.collections.Set<${element.type}>",
        "Set<${element.shortType}>",
    ) {
        override val imports get() = element.imports

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%L.toNbtTag())", listVar, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)
    }

    data class Nested(val name: String) : SerialType(name, name, setOf(name), dummy, dummy) {
        override fun emitNbtPut(b: CodeBlock.Builder, tagVar: String, key: String, value: String, keyIsVar: Boolean) {
            if (keyIsVar) b.addStatement("%L.put(%L, %L.toNbt())", tagVar, key, value)
            else b.addStatement("%L.put(%S, %L.toNbt())", tagVar, key, value)
        }

        override fun nbtGet(tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
            if (keyIsVar) CodeBlock.of("%L.fromNbt(%L.getCompound(%L))", name, tagVar, key)
            else CodeBlock.of("%L.fromNbt(%L.getCompound(%S))", name, tagVar, key)

        override fun emitBufWrite(b: CodeBlock.Builder, value: String) {
            b.addStatement("%L.write(buf)", value)
        }

        override fun bufRead(): CodeBlock = CodeBlock.of("%L.read(buf)", name)

        override fun emitListAdd(b: CodeBlock.Builder, listVar: String, itemVar: String) {
            b.addStatement("%L.add(%L.toNbt())", listVar, itemVar)
        }

        override fun listTag(): CodeBlock = CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)

        override fun listGet(listVar: String): CodeBlock = CodeBlock.of("%L.fromNbt(%L.getCompound(i))", name, listVar)
    }

    companion object {
        private val primitives: List<SerialType> = listOf(
            Bool, F32, F64, Str,
            I8, I16, I32, I64, VarI32, VarI64,
            U8, U16, U32, U64, VarU32, VarU64,
            BigInt, VarBigInt,
            I8Array, I32Array, I64Array,
            UUID, ResLoc, BlockPos,
        )

        private val byName: Map<String, SerialType> by lazy {
            primitives.flatMap { type -> type.types.map { name -> name to type } }.toMap()
        }

        fun primitive(qn: String, varLen: Boolean = false): SerialType? {
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
