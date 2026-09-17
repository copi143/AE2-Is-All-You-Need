package io.github.copi143.serialization.gen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec

private val COMPOUND_TAG = ClassName("net.minecraft.nbt", "CompoundTag")
private val LIST_TAG = ClassName("net.minecraft.nbt", "ListTag")
private val STRING_TAG = ClassName("net.minecraft.nbt", "StringTag")
private val TAG = ClassName("net.minecraft.nbt", "Tag")
private val BYTE_TAG = ClassName("net.minecraft.nbt", "ByteTag")
private val SHORT_TAG = ClassName("net.minecraft.nbt", "ShortTag")
private val INT_TAG = ClassName("net.minecraft.nbt", "IntTag")
private val LONG_TAG = ClassName("net.minecraft.nbt", "LongTag")
private val NUMERIC_TAG = ClassName("net.minecraft.nbt", "NumericTag")
private val FRIENDLY_BUF = ClassName("net.minecraft.network", "FriendlyByteBuf")
private val BIG_INTEGER = ClassName("java.math", "BigInteger")
private val UUID = ClassName("java.util", "UUID")
private val RESOURCE_LOCATION = ClassName("net.minecraft.resources", "ResourceLocation")
private val BLOCK_POS = ClassName("net.minecraft.core", "BlockPos")

internal fun emit(cls: SerialClass): FileSpec {
    val file = FileSpec.builder(cls.pkg, "${cls.name}Serdes")
    file.addFunction(toNbtFun(cls))
    file.addFunction(writeFun(cls))
    if (!cls.construct) file.addFunction(readIntoFun(cls))
    file.addFunction(fromNbtFun(cls))
    file.addFunction(readFun(cls))
    return file.build()
}

private fun toNbtFun(cls: SerialClass): FunSpec {
    val body = CodeBlock.builder()
    body.addStatement("val tag = %T()", COMPOUND_TAG)
    for (f in cls.fields) {
        if (f.nullable) {
            body.beginControlFlow("if (%N != null)", f.name)
            nbtPut(body, f, "${f.name}!!")
            body.endControlFlow()
        } else nbtPut(body, f, f.name)
    }
    body.addStatement("return tag")
    return FunSpec.builder("toNbt")
        .receiver(ClassName(cls.pkg, cls.name))
        .returns(COMPOUND_TAG)
        .addCode(body.build())
        .build()
}

private fun nbtPut(body: CodeBlock.Builder, f: SerialProp, acc: String) {
    when (val t = f.type) {
        SerialTy.Bool -> body.addStatement("tag.putBoolean(%S, %L)", f.wireName, acc)
        SerialTy.I8 -> body.addStatement("tag.putByte(%S, %L)", f.wireName, acc)
        SerialTy.I16 -> body.addStatement("tag.putShort(%S, %L)", f.wireName, acc)
        SerialTy.I32 -> body.addStatement("tag.putInt(%S, %L)", f.wireName, acc)
        SerialTy.I64 -> body.addStatement("tag.putLong(%S, %L)", f.wireName, acc)
        SerialTy.U8 -> body.addStatement("tag.putByte(%S, %L.toByte())", f.wireName, acc)
        SerialTy.U16 -> body.addStatement("tag.putShort(%S, %L.toShort())", f.wireName, acc)
        SerialTy.U32 -> body.addStatement("tag.putInt(%S, %L.toInt())", f.wireName, acc)
        SerialTy.U64 -> body.addStatement("tag.putLong(%S, %L.toLong())", f.wireName, acc)
        SerialTy.F32 -> body.addStatement("tag.putFloat(%S, %L)", f.wireName, acc)
        SerialTy.F64 -> body.addStatement("tag.putDouble(%S, %L)", f.wireName, acc)
        SerialTy.Str -> body.addStatement("tag.putString(%S, %L)", f.wireName, acc)
        SerialTy.Bytes -> body.addStatement("tag.putByteArray(%S, %L)", f.wireName, acc)
        SerialTy.Ints -> body.addStatement("tag.putIntArray(%S, %L)", f.wireName, acc)
        SerialTy.Longs -> body.addStatement("tag.putLongArray(%S, %L)", f.wireName, acc)
        SerialTy.BigInt, SerialTy.VarBigInt ->
            body.addStatement("tag.putByteArray(%S, %L.toByteArray())", f.wireName, acc)
        SerialTy.Uuid -> body.addStatement("tag.putUUID(%S, %L)", f.wireName, acc)
        SerialTy.ResLoc -> body.addStatement("tag.putString(%S, %L.toString())", f.wireName, acc)
        SerialTy.BlockPos -> body.addStatement("tag.putLong(%S, %L.asLong())", f.wireName, acc)
        is SerialTy.Enum ->
            if (t.ordinal) body.addStatement("tag.putByte(%S, %L.ordinal.toByte())", f.wireName, acc)
            else body.addStatement("tag.putString(%S, %L.name)", f.wireName, acc)
        is SerialTy.Nested -> body.addStatement("tag.put(%S, %L.toNbt())", f.wireName, acc)
        is SerialTy.ListOf -> {
            body.beginControlFlow("run")
            body.addStatement("val list = %T()", LIST_TAG)
            body.beginControlFlow("for (item in %L)", acc)
            putListElement(body, "list", "item", t.element)
            body.endControlFlow()
            body.addStatement("tag.put(%S, list)", f.wireName)
            body.endControlFlow()
        }
        is SerialTy.MapOf -> {
            if (t.key == SerialTy.Str) {
                body.beginControlFlow("run")
                body.addStatement("val mapTag = %T()", COMPOUND_TAG)
                body.beginControlFlow("for ((k, v) in %L)", acc)
                nbtPutValueDynamic(body, "mapTag", "k", "v", t.value)
                body.endControlFlow()
                body.addStatement("tag.put(%S, mapTag)", f.wireName)
                body.endControlFlow()
            } else {
                body.beginControlFlow("run")
                body.addStatement("val list = %T()", LIST_TAG)
                body.beginControlFlow("for ((k, v) in %L)", acc)
                body.addStatement("val entry = %T()", COMPOUND_TAG)
                nbtPutValue(body, "entry", "k", "k", t.key)
                nbtPutValue(body, "entry", "v", "v", t.value)
                body.addStatement("list.add(entry)")
                body.endControlFlow()
                body.addStatement("tag.put(%S, list)", f.wireName)
                body.endControlFlow()
            }
        }
        is SerialTy.SetOf -> {
            body.beginControlFlow("run")
            body.addStatement("val list = %T()", LIST_TAG)
            body.beginControlFlow("for (item in %L)", acc)
            putListElement(body, "list", "item", t.element)
            body.endControlFlow()
            body.addStatement("tag.put(%S, list)", f.wireName)
            body.endControlFlow()
        }
        SerialTy.VarInt -> body.addStatement("tag.putInt(%S, %L)", f.wireName, acc)
        SerialTy.VarLong -> body.addStatement("tag.putLong(%S, %L)", f.wireName, acc)
        SerialTy.VarU32 -> body.addStatement("tag.putInt(%S, %L.toInt())", f.wireName, acc)
        SerialTy.VarU64 -> body.addStatement("tag.putLong(%S, %L.toLong())", f.wireName, acc)
    }
}

private fun putListElement(body: CodeBlock.Builder, listVar: String, itemVar: String, elem: SerialTy) {
    when (elem) {
        SerialTy.Str -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, STRING_TAG, itemVar)
        SerialTy.I32 -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, INT_TAG, itemVar)
        SerialTy.I64 -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, LONG_TAG, itemVar)
        SerialTy.U8 -> body.addStatement("%L.add(%T.valueOf(%L.toByte()))", listVar, BYTE_TAG, itemVar)
        SerialTy.U16 -> body.addStatement("%L.add(%T.valueOf(%L.toShort()))", listVar, SHORT_TAG, itemVar)
        SerialTy.U32 -> body.addStatement("%L.add(%T.valueOf(%L.toInt()))", listVar, INT_TAG, itemVar)
        SerialTy.U64 -> body.addStatement("%L.add(%T.valueOf(%L.toLong()))", listVar, LONG_TAG, itemVar)
        SerialTy.Bool -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, BYTE_TAG, itemVar)
        is SerialTy.Nested -> body.addStatement("%L.add(%L.toNbt())", listVar, itemVar)
        is SerialTy.Enum ->
            if (elem.ordinal) body.addStatement("%L.add(%T.valueOf(%L.ordinal.toByte()))", listVar, BYTE_TAG, itemVar)
            else body.addStatement("%L.add(%T.valueOf(%L.name))", listVar, STRING_TAG, itemVar)
        is SerialTy.ListOf -> {
            body.addStatement("val __inner = %T()", LIST_TAG)
            body.beginControlFlow("for (__e in %L)", itemVar)
            putListElement(body, "__inner", "__e", elem.element)
            body.endControlFlow()
            body.addStatement("%L.add(__inner)", listVar)
        }
        is SerialTy.MapOf, is SerialTy.SetOf -> body.addStatement("%L.add(%L.toNbtTag())", listVar, itemVar)
        else -> body.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, STRING_TAG, itemVar)
    }
}

private fun nbtPutValue(body: CodeBlock.Builder, tagVar: String, key: String, acc: String, type: SerialTy) {
    when (type) {
        SerialTy.Bool -> body.addStatement("%L.putBoolean(%S, %L)", tagVar, key, acc)
        SerialTy.I8 -> body.addStatement("%L.putByte(%S, %L)", tagVar, key, acc)
        SerialTy.I16 -> body.addStatement("%L.putShort(%S, %L)", tagVar, key, acc)
        SerialTy.I32, SerialTy.VarInt -> body.addStatement("%L.putInt(%S, %L)", tagVar, key, acc)
        SerialTy.I64, SerialTy.VarLong -> body.addStatement("%L.putLong(%S, %L)", tagVar, key, acc)
        SerialTy.U8 -> body.addStatement("%L.putByte(%S, %L.toByte())", tagVar, key, acc)
        SerialTy.U16 -> body.addStatement("%L.putShort(%S, %L.toShort())", tagVar, key, acc)
        SerialTy.U32, SerialTy.VarU32 -> body.addStatement("%L.putInt(%S, %L.toInt())", tagVar, key, acc)
        SerialTy.U64, SerialTy.VarU64 -> body.addStatement("%L.putLong(%S, %L.toLong())", tagVar, key, acc)
        SerialTy.F32 -> body.addStatement("%L.putFloat(%S, %L)", tagVar, key, acc)
        SerialTy.F64 -> body.addStatement("%L.putDouble(%S, %L)", tagVar, key, acc)
        SerialTy.Str -> body.addStatement("%L.putString(%S, %L)", tagVar, key, acc)
        SerialTy.Bytes -> body.addStatement("%L.putByteArray(%S, %L)", tagVar, key, acc)
        SerialTy.Ints -> body.addStatement("%L.putIntArray(%S, %L)", tagVar, key, acc)
        SerialTy.Longs -> body.addStatement("%L.putLongArray(%S, %L)", tagVar, key, acc)
        SerialTy.BigInt, SerialTy.VarBigInt -> body.addStatement("%L.putByteArray(%S, %L.toByteArray())", tagVar, key, acc)
        SerialTy.Uuid -> body.addStatement("%L.putUUID(%S, %L)", tagVar, key, acc)
        SerialTy.ResLoc -> body.addStatement("%L.putString(%S, %L.toString())", tagVar, key, acc)
        SerialTy.BlockPos -> body.addStatement("%L.putLong(%S, %L.asLong())", tagVar, key, acc)
        is SerialTy.Enum -> if (type.ordinal) body.addStatement("%L.putByte(%S, %L.ordinal.toByte())", tagVar, key, acc) else body.addStatement("%L.putString(%S, %L.name)", tagVar, key, acc)
        is SerialTy.Nested -> body.addStatement("%L.put(%S, %L.toNbt())", tagVar, key, acc)
        is SerialTy.ListOf -> {
            body.addStatement("val __list = %T()", LIST_TAG)
            body.beginControlFlow("for (__item in %L)", acc)
            putListElement(body, "__list", "__item", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%S, __list)", tagVar, key)
        }
        is SerialTy.MapOf -> {
            if (type.key == SerialTy.Str) {
                body.addStatement("val __m = %T()", COMPOUND_TAG)
                body.beginControlFlow("for ((__k, __v) in %L)", acc)
                nbtPutValueDynamic(body, "__m", "__k", "__v", type.value)
                body.endControlFlow()
                body.addStatement("%L.put(%S, __m)", tagVar, key)
            } else {
                body.addStatement("val __ml = %T()", LIST_TAG)
                body.beginControlFlow("for ((__k, __v) in %L)", acc)
                body.addStatement("val __e = %T()", COMPOUND_TAG)
                nbtPutValue(body, "__e", "k", "__k", type.key)
                nbtPutValue(body, "__e", "v", "__v", type.value)
                body.addStatement("__ml.add(__e)")
                body.endControlFlow()
                body.addStatement("%L.put(%S, __ml)", tagVar, key)
            }
        }
        is SerialTy.SetOf -> {
            body.addStatement("val __s = %T()", LIST_TAG)
            body.beginControlFlow("for (__item in %L)", acc)
            putListElement(body, "__s", "__item", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%S, __s)", tagVar, key)
        }
    }
}

private fun nbtPutValueDynamic(body: CodeBlock.Builder, tagVar: String, keyVar: String, acc: String, type: SerialTy) {
    when (type) {
        SerialTy.Bool -> body.addStatement("%L.putBoolean(%L, %L)", tagVar, keyVar, acc)
        SerialTy.I8 -> body.addStatement("%L.putByte(%L, %L)", tagVar, keyVar, acc)
        SerialTy.I16 -> body.addStatement("%L.putShort(%L, %L)", tagVar, keyVar, acc)
        SerialTy.I32, SerialTy.VarInt -> body.addStatement("%L.putInt(%L, %L)", tagVar, keyVar, acc)
        SerialTy.I64, SerialTy.VarLong -> body.addStatement("%L.putLong(%L, %L)", tagVar, keyVar, acc)
        SerialTy.U8 -> body.addStatement("%L.putByte(%L, %L.toByte())", tagVar, keyVar, acc)
        SerialTy.U16 -> body.addStatement("%L.putShort(%L, %L.toShort())", tagVar, keyVar, acc)
        SerialTy.U32, SerialTy.VarU32 -> body.addStatement("%L.putInt(%L, %L.toInt())", tagVar, keyVar, acc)
        SerialTy.U64, SerialTy.VarU64 -> body.addStatement("%L.putLong(%L, %L.toLong())", tagVar, keyVar, acc)
        SerialTy.F32 -> body.addStatement("%L.putFloat(%L, %L)", tagVar, keyVar, acc)
        SerialTy.F64 -> body.addStatement("%L.putDouble(%L, %L)", tagVar, keyVar, acc)
        SerialTy.Str -> body.addStatement("%L.putString(%L, %L)", tagVar, keyVar, acc)
        SerialTy.Bytes -> body.addStatement("%L.putByteArray(%L, %L)", tagVar, keyVar, acc)
        SerialTy.Ints -> body.addStatement("%L.putIntArray(%L, %L)", tagVar, keyVar, acc)
        SerialTy.Longs -> body.addStatement("%L.putLongArray(%L, %L)", tagVar, keyVar, acc)
        SerialTy.BigInt, SerialTy.VarBigInt -> body.addStatement("%L.putByteArray(%L, %L.toByteArray())", tagVar, keyVar, acc)
        SerialTy.Uuid -> body.addStatement("%L.putUUID(%L, %L)", tagVar, keyVar, acc)
        SerialTy.ResLoc -> body.addStatement("%L.putString(%L, %L.toString())", tagVar, keyVar, acc)
        SerialTy.BlockPos -> body.addStatement("%L.putLong(%L, %L.asLong())", tagVar, keyVar, acc)
        is SerialTy.Enum -> if (type.ordinal) body.addStatement("%L.putByte(%L, %L.ordinal.toByte())", tagVar, keyVar, acc) else body.addStatement("%L.putString(%L, %L.name)", tagVar, keyVar, acc)
        is SerialTy.Nested -> body.addStatement("%L.put(%L, %L.toNbt())", tagVar, keyVar, acc)
        is SerialTy.ListOf -> {
            body.addStatement("val __ld = %T()", LIST_TAG)
            body.beginControlFlow("for (__it in %L)", acc)
            putListElement(body, "__ld", "__it", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%L, __ld)", tagVar, keyVar)
        }
        is SerialTy.MapOf, is SerialTy.SetOf -> nbtPutValue(body, tagVar, keyVar, acc, type)
    }
}

private fun fromNbtFun(cls: SerialClass): FunSpec {
    val self = ClassName(cls.pkg, cls.name)
    val body = CodeBlock.builder()
    if (!cls.construct) {
        body.addStatement("val v = %L()", cls.name)
        body.addStatement("v.readInto(tag)")
        body.addStatement("return v")
    } else {
        for (f in cls.fields) {
            if (f.nullable) {
                body.addStatement(
                    "val %N = if (tag.contains(%S)) %L else null",
                    f.name, f.wireName, nbtRead(f.type, f.wireName),
                )
            } else when (val t = f.type) {
                is SerialTy.ListOf -> {
                    body.addStatement(
                        "val %N = tag.getList(%S, %L)",
                        "_${f.name}", f.wireName, listTagType(t.element),
                    )
                    body.addStatement(
                        "val %N = ArrayList<%L>(%N.size)",
                        f.name, typeKt(t.element), "_${f.name}",
                    )
                    body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                    body.addStatement("%N.add(%L)", f.name, listGet(t.element, "_${f.name}"))
                    body.endControlFlow()
                }
                is SerialTy.SetOf -> {
                    body.addStatement(
                        "val %N = tag.getList(%S, %L)",
                        "_${f.name}", f.wireName, listTagType(t.element),
                    )
                    body.addStatement(
                        "val %N = LinkedHashSet<%L>(%N.size)",
                        f.name, typeKt(t.element), "_${f.name}",
                    )
                    body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                    body.addStatement("%N.add(%L)", f.name, listGet(t.element, "_${f.name}"))
                    body.endControlFlow()
                }
                is SerialTy.MapOf -> {
                    if (t.key == SerialTy.Str) {
                        body.addStatement("val %N = tag.getCompound(%S)", "_${f.name}", f.wireName)
                        body.addStatement(
                            "val %N = LinkedHashMap<%L, %L>(%N.allKeys.size)",
                            f.name, typeKt(t.key), typeKt(t.value), "_${f.name}",
                        )
                        body.beginControlFlow("for (k in %N.allKeys)", "_${f.name}")
                        body.addStatement("val __v = %L", nbtGetValueDynamic(t.value, "_${f.name}", "k"))
                        body.addStatement("%N[k] = __v", f.name)
                        body.endControlFlow()
                    } else {
                        body.addStatement(
                            "val %N = tag.getList(%S, %T.TAG_COMPOUND.toInt())",
                            "_${f.name}", f.wireName, TAG,
                        )
                        body.addStatement(
                            "val %N = LinkedHashMap<%L, %L>(%N.size)",
                            f.name, typeKt(t.key), typeKt(t.value), "_${f.name}",
                        )
                        body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                        body.addStatement("val __e = %N.getCompound(i)", "_${f.name}")
                        body.addStatement("val __k = %L", nbtGetValue(t.key, "__e", "k"))
                        body.addStatement("val __v = %L", nbtGetValue(t.value, "__e", "v"))
                        body.addStatement("%N[__k] = __v", f.name)
                        body.endControlFlow()
                    }
                }
                else -> body.addStatement("val %N = %L", f.name, nbtRead(t, f.wireName))
            }
        }
        body.addStatement("return %L(%L)", cls.name, cls.fields.joinToString(", ") { it.name })
    }
    return FunSpec.builder("fromNbt")
        .receiver(self.nestedClass("Companion"))
        .addParameter("tag", COMPOUND_TAG)
        .returns(self)
        .addCode(body.build())
        .build()
}

private fun nbtRead(t: SerialTy, k: String): CodeBlock = nbtGetValue(t, "tag", k)

private fun nbtGetValue(t: SerialTy, tagVar: String, k: String): CodeBlock = when (t) {
    SerialTy.VarInt -> CodeBlock.of("%L.getInt(%S)", tagVar, k)
    SerialTy.VarLong -> CodeBlock.of("%L.getLong(%S)", tagVar, k)
    SerialTy.VarU32 -> CodeBlock.of("%L.getInt(%S).toUInt()", tagVar, k)
    SerialTy.VarU64 -> CodeBlock.of("%L.getLong(%S).toULong()", tagVar, k)
    SerialTy.Bool -> CodeBlock.of("%L.getBoolean(%S)", tagVar, k)
    SerialTy.I8 -> CodeBlock.of("%L.getByte(%S)", tagVar, k)
    SerialTy.I16 -> CodeBlock.of("%L.getShort(%S)", tagVar, k)
    SerialTy.I32 -> CodeBlock.of("%L.getInt(%S)", tagVar, k)
    SerialTy.I64 -> CodeBlock.of("%L.getLong(%S)", tagVar, k)
    SerialTy.U8 -> CodeBlock.of("%L.getByte(%S).toUByte()", tagVar, k)
    SerialTy.U16 -> CodeBlock.of("%L.getShort(%S).toUShort()", tagVar, k)
    SerialTy.U32 -> CodeBlock.of("%L.getInt(%S).toUInt()", tagVar, k)
    SerialTy.U64 -> CodeBlock.of("%L.getLong(%S).toULong()", tagVar, k)
    SerialTy.F32 -> CodeBlock.of("%L.getFloat(%S)", tagVar, k)
    SerialTy.F64 -> CodeBlock.of("%L.getDouble(%S)", tagVar, k)
    SerialTy.Str -> CodeBlock.of("%L.getString(%S)", tagVar, k)
    SerialTy.Bytes -> CodeBlock.of("%L.getByteArray(%S)", tagVar, k)
    SerialTy.Ints -> CodeBlock.of("%L.getIntArray(%S)", tagVar, k)
    SerialTy.Longs -> CodeBlock.of("%L.getLongArray(%S)", tagVar, k)
    SerialTy.BigInt, SerialTy.VarBigInt -> CodeBlock.of("%T(%L.getByteArray(%S))", BIG_INTEGER, tagVar, k)
    SerialTy.Uuid -> CodeBlock.of("%L.getUUID(%S)", tagVar, k)
    SerialTy.ResLoc -> CodeBlock.of("%T(%L.getString(%S))", RESOURCE_LOCATION, tagVar, k)
    SerialTy.BlockPos -> CodeBlock.of("%T.of(%L.getLong(%S))", BLOCK_POS, tagVar, k)
    is SerialTy.Enum -> if (t.ordinal) CodeBlock.of("%L.entries[%L.getByte(%S).toInt() and 0xFF]", t.name, tagVar, k)
    else CodeBlock.of("%L.valueOf(%L.getString(%S))", t.name, tagVar, k)
    is SerialTy.Nested -> CodeBlock.of("%L.fromNbt(%L.getCompound(%S))", t.name, tagVar, k)
    is SerialTy.ListOf, is SerialTy.MapOf, is SerialTy.SetOf -> error("collection handled separately")
}

private fun nbtGetValueDynamic(t: SerialTy, tagVar: String, keyVar: String): CodeBlock = when (t) {
    SerialTy.Bool -> CodeBlock.of("%L.getBoolean(%L)", tagVar, keyVar)
    SerialTy.I8 -> CodeBlock.of("%L.getByte(%L)", tagVar, keyVar)
    SerialTy.I16 -> CodeBlock.of("%L.getShort(%L)", tagVar, keyVar)
    SerialTy.I32, SerialTy.VarInt -> CodeBlock.of("%L.getInt(%L)", tagVar, keyVar)
    SerialTy.I64, SerialTy.VarLong -> CodeBlock.of("%L.getLong(%L)", tagVar, keyVar)
    SerialTy.U8 -> CodeBlock.of("%L.getByte(%L).toUByte()", tagVar, keyVar)
    SerialTy.U16 -> CodeBlock.of("%L.getShort(%L).toUShort()", tagVar, keyVar)
    SerialTy.U32, SerialTy.VarU32 -> CodeBlock.of("%L.getInt(%L).toUInt()", tagVar, keyVar)
    SerialTy.U64, SerialTy.VarU64 -> CodeBlock.of("%L.getLong(%L).toULong()", tagVar, keyVar)
    SerialTy.F32 -> CodeBlock.of("%L.getFloat(%L)", tagVar, keyVar)
    SerialTy.F64 -> CodeBlock.of("%L.getDouble(%L)", tagVar, keyVar)
    SerialTy.Str -> CodeBlock.of("%L.getString(%L)", tagVar, keyVar)
    SerialTy.Bytes -> CodeBlock.of("%L.getByteArray(%L)", tagVar, keyVar)
    SerialTy.Ints -> CodeBlock.of("%L.getIntArray(%L)", tagVar, keyVar)
    SerialTy.Longs -> CodeBlock.of("%L.getLongArray(%L)", tagVar, keyVar)
    SerialTy.BigInt, SerialTy.VarBigInt -> CodeBlock.of("%T(%L.getByteArray(%L))", BIG_INTEGER, tagVar, keyVar)
    SerialTy.Uuid -> CodeBlock.of("%L.getUUID(%L)", tagVar, keyVar)
    SerialTy.ResLoc -> CodeBlock.of("%T(%L.getString(%L))", RESOURCE_LOCATION, tagVar, keyVar)
    SerialTy.BlockPos -> CodeBlock.of("%T.of(%L.getLong(%L))", BLOCK_POS, tagVar, keyVar)
    is SerialTy.Enum -> if (t.ordinal) CodeBlock.of("%L.entries[%L.getByte(%L).toInt() and 0xFF]", t.name, tagVar, keyVar)
    else CodeBlock.of("%L.valueOf(%L.getString(%L))", t.name, tagVar, keyVar)
    is SerialTy.Nested -> CodeBlock.of("%L.fromNbt(%L.getCompound(%L))", t.name, tagVar, keyVar)
    is SerialTy.ListOf -> CodeBlock.of(
        "run { val __l = %L.getList(%L, %L); val __r = ArrayList<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r }",
        tagVar, keyVar, listTagType(t.element), typeKt(t.element), listGet(t.element, "__l"),
    )
    is SerialTy.SetOf -> CodeBlock.of(
        "run { val __l = %L.getList(%L, %L); val __r = LinkedHashSet<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r }",
        tagVar, keyVar, listTagType(t.element), typeKt(t.element), listGet(t.element, "__l"),
    )
    is SerialTy.MapOf -> if (t.key == SerialTy.Str) CodeBlock.of(
        "run { val __m = %L.getCompound(%L); val __r = LinkedHashMap<%L, %L>(__m.allKeys.size); for (__kk in __m.allKeys) __r[__kk] = %L; __r }",
        tagVar, keyVar, typeKt(t.key), typeKt(t.value), nbtGetValueDynamic(t.value, "__m", "__kk"),
    ) else CodeBlock.of(
        "run { val __l = %L.getList(%L, %T.TAG_COMPOUND.toInt()); val __r = LinkedHashMap<%L, %L>(__l.size); for (i in __l.indices) { val __e = __l.getCompound(i); val __k = %L; val __v = %L; __r[__k] = __v }; __r }",
        tagVar, keyVar, TAG, typeKt(t.key), typeKt(t.value), nbtGetValue(t.key, "__e", "k"), nbtGetValue(t.value, "__e", "v"),
    )
}

private fun listTagType(elem: SerialTy): CodeBlock = when (elem) {
    SerialTy.Str -> CodeBlock.of("%T.TAG_STRING.toInt()", TAG)
    SerialTy.I32 -> CodeBlock.of("%T.TAG_INT.toInt()", TAG)
    SerialTy.I64 -> CodeBlock.of("%T.TAG_LONG.toInt()", TAG)
    SerialTy.I8, SerialTy.Bool, SerialTy.U8 -> CodeBlock.of("%T.TAG_BYTE.toInt()", TAG)
    SerialTy.U16 -> CodeBlock.of("%T.TAG_SHORT.toInt()", TAG)
    SerialTy.U32 -> CodeBlock.of("%T.TAG_INT.toInt()", TAG)
    SerialTy.U64 -> CodeBlock.of("%T.TAG_LONG.toInt()", TAG)
    is SerialTy.Nested -> CodeBlock.of("%T.TAG_COMPOUND.toInt()", TAG)
    is SerialTy.Enum -> if (elem.ordinal) CodeBlock.of("%T.TAG_BYTE.toInt()", TAG)
    else CodeBlock.of("%T.TAG_STRING.toInt()", TAG)
    else -> CodeBlock.of("%T.TAG_COMPOUND.toInt()", TAG)
}

private fun listGet(elem: SerialTy, list: String): CodeBlock = when (elem) {
    SerialTy.Str -> CodeBlock.of("%L.getString(i)", list)
    SerialTy.I32 -> CodeBlock.of("(%L.get(i) as %T).getAsInt()", list, NUMERIC_TAG)
    SerialTy.I64 -> CodeBlock.of("(%L.get(i) as %T).getAsLong()", list, NUMERIC_TAG)
    SerialTy.Bool -> CodeBlock.of("(%L.get(i) as %T).getAsByte().toInt() != 0", list, NUMERIC_TAG)
    SerialTy.U8 -> CodeBlock.of("(%L.get(i) as %T).getAsByte().toUByte()", list, NUMERIC_TAG)
    SerialTy.U16 -> CodeBlock.of("(%L.get(i) as %T).getAsShort().toUShort()", list, NUMERIC_TAG)
    SerialTy.U32 -> CodeBlock.of("(%L.get(i) as %T).getAsInt().toUInt()", list, NUMERIC_TAG)
    SerialTy.U64 -> CodeBlock.of("(%L.get(i) as %T).getAsLong().toULong()", list, NUMERIC_TAG)
    is SerialTy.Nested -> CodeBlock.of("%L.fromNbt(%L.getCompound(i))", elem.name, list)
    is SerialTy.Enum -> if (elem.ordinal) CodeBlock.of(
        "%L.entries[(%L.get(i) as %T).getAsByte().toInt() and 0xFF]",
        elem.name, list, NUMERIC_TAG,
    ) else CodeBlock.of("%L.valueOf(%L.getString(i))", elem.name, list)
    else -> error("unsupported $elem")
}

private fun typeKt(t: SerialTy): String = when (t) {
    SerialTy.Bool -> "Boolean"
    SerialTy.I8 -> "Byte"
    SerialTy.I16 -> "Short"
    SerialTy.I32, SerialTy.VarInt -> "Int"
    SerialTy.I64, SerialTy.VarLong -> "Long"
    SerialTy.U8 -> "UByte"
    SerialTy.U16 -> "UShort"
    SerialTy.U32, SerialTy.VarU32 -> "UInt"
    SerialTy.U64, SerialTy.VarU64 -> "ULong"
    SerialTy.F32 -> "Float"
    SerialTy.F64 -> "Double"
    SerialTy.Str -> "String"
    SerialTy.Bytes -> "ByteArray"
    SerialTy.Ints -> "IntArray"
    SerialTy.Longs -> "LongArray"
    SerialTy.BigInt, SerialTy.VarBigInt -> "BigInteger"
    SerialTy.Uuid -> "UUID"
    SerialTy.ResLoc -> "ResourceLocation"
    SerialTy.BlockPos -> "BlockPos"
    is SerialTy.Enum -> t.name
    is SerialTy.Nested -> t.name
    is SerialTy.ListOf -> "List<${typeKt(t.element)}>"
    is SerialTy.MapOf -> "Map<${typeKt(t.key)}, ${typeKt(t.value)}>"
    is SerialTy.SetOf -> "Set<${typeKt(t.element)}>"
}

private fun writeFun(cls: SerialClass): FunSpec {
    val body = CodeBlock.builder()
    for (f in cls.fields) {
        if (f.nullable) {
            body.addStatement("buf.writeBoolean(%N != null)", f.name)
            body.beginControlFlow("if (%N != null)", f.name)
            bufWrite(body, f.type, "${f.name}!!")
            body.endControlFlow()
        } else bufWrite(body, f.type, f.name)
    }
    return FunSpec.builder("write")
        .receiver(ClassName(cls.pkg, cls.name))
        .addParameter("buf", FRIENDLY_BUF)
        .addCode(body.build())
        .build()
}

private fun bufWrite(body: CodeBlock.Builder, t: SerialTy, acc: String) {
    when (t) {
        SerialTy.Bool -> body.addStatement("buf.writeBoolean(%L)", acc)
        SerialTy.I8 -> body.addStatement("buf.writeByte(%L.toInt())", acc)
        SerialTy.I16 -> body.addStatement("buf.writeShort(%L.toInt())", acc)
        SerialTy.I32 -> body.addStatement("buf.writeInt(%L)", acc)
        SerialTy.I64 -> body.addStatement("buf.writeLong(%L)", acc)
        SerialTy.U8 -> body.addStatement("buf.writeByte(%L.toInt())", acc)
        SerialTy.U16 -> body.addStatement("buf.writeShort(%L.toInt())", acc)
        SerialTy.U32 -> body.addStatement("buf.writeInt(%L.toInt())", acc)
        SerialTy.U64 -> body.addStatement("buf.writeLong(%L.toLong())", acc)
        SerialTy.VarU32 -> body.addStatement("buf.writeVarInt(%L.toInt())", acc)
        SerialTy.VarU64 -> body.addStatement("buf.writeVarLong(%L.toLong())", acc)
        SerialTy.VarInt -> body.addStatement("buf.writeVarInt(%L)", acc)
        SerialTy.VarLong -> body.addStatement("buf.writeVarLong(%L)", acc)
        SerialTy.F32 -> body.addStatement("buf.writeFloat(%L)", acc)
        SerialTy.F64 -> body.addStatement("buf.writeDouble(%L)", acc)
        SerialTy.Str -> body.addStatement("buf.writeUtf(%L)", acc)
        SerialTy.Bytes -> body.addStatement("buf.writeByteArray(%L)", acc)
        SerialTy.Ints -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.addStatement("for (n in %L) buf.writeInt(n)", acc)
        }
        SerialTy.Longs -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.addStatement("for (n in %L) buf.writeLong(n)", acc)
        }
        SerialTy.BigInt, SerialTy.VarBigInt -> body.addStatement("buf.writeByteArray(%L.toByteArray())", acc)
        SerialTy.Uuid -> body.addStatement("buf.writeUUID(%L)", acc)
        SerialTy.ResLoc -> body.addStatement("buf.writeUtf(%L.toString())", acc)
        SerialTy.BlockPos -> body.addStatement("buf.writeLong(%L.asLong())", acc)
        is SerialTy.Enum ->
            if (t.ordinal) body.addStatement("buf.writeByte(%L.ordinal)", acc)
            else body.addStatement("buf.writeUtf(%L.name)", acc)
        is SerialTy.Nested -> body.addStatement("%L.write(buf)", acc)
        is SerialTy.ListOf -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.beginControlFlow("for (item in %L)", acc)
            bufWrite(body, t.element, "item")
            body.endControlFlow()
        }
        is SerialTy.MapOf -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.beginControlFlow("for ((k, v) in %L)", acc)
            bufWrite(body, t.key, "k")
            bufWrite(body, t.value, "v")
            body.endControlFlow()
        }
        is SerialTy.SetOf -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.beginControlFlow("for (item in %L)", acc)
            bufWrite(body, t.element, "item")
            body.endControlFlow()
        }
    }
}

private fun readFun(cls: SerialClass): FunSpec {
    val self = ClassName(cls.pkg, cls.name)
    val body = CodeBlock.builder()
    if (!cls.construct) {
        body.addStatement("val v = %L()", cls.name)
        for (f in cls.fields) {
            if (f.nullable) body.addStatement("v.%N = if (buf.readBoolean()) %L else null", f.name, bufRead(f.type))
            else body.addStatement("v.%N = %L", f.name, bufRead(f.type))
        }
        body.addStatement("return v")
    } else {
        for (f in cls.fields) {
            if (f.nullable) body.addStatement("val %N = if (buf.readBoolean()) %L else null", f.name, bufRead(f.type))
            else when (val t = f.type) {
                is SerialTy.MapOf -> {
                    body.addStatement("val %N = run {", f.name)
                    body.indent()
                    body.addStatement("val __n = buf.readVarInt()")
                    body.addStatement("val __m = LinkedHashMap<%L, %L>(__n)", typeKt(t.key), typeKt(t.value))
                    body.beginControlFlow("repeat(__n)")
                    body.addStatement("val __k = %L", bufRead(t.key))
                    body.addStatement("val __v = %L", bufRead(t.value))
                    body.addStatement("__m[__k] = __v")
                    body.endControlFlow()
                    body.addStatement("__m")
                    body.unindent()
                    body.addStatement("}")
                }
                is SerialTy.SetOf -> {
                    body.addStatement("val %N = run {", f.name)
                    body.indent()
                    body.addStatement("val __n = buf.readVarInt()")
                    body.addStatement("val __s = LinkedHashSet<%L>(__n)", typeKt(t.element))
                    body.beginControlFlow("repeat(__n)")
                    body.addStatement("__s.add(%L)", bufRead(t.element))
                    body.endControlFlow()
                    body.addStatement("__s")
                    body.unindent()
                    body.addStatement("}")
                }
                else -> body.addStatement("val %N = %L", f.name, bufRead(f.type))
            }
        }
        body.addStatement("return %L(%L)", cls.name, cls.fields.joinToString(", ") { it.name })
    }
    return FunSpec.builder("read")
        .receiver(self.nestedClass("Companion"))
        .addParameter("buf", FRIENDLY_BUF)
        .returns(self)
        .addCode(body.build())
        .build()
}

private fun bufRead(t: SerialTy): CodeBlock = when (t) {
    SerialTy.Bool -> CodeBlock.of("buf.readBoolean()")
    SerialTy.I8 -> CodeBlock.of("buf.readByte()")
    SerialTy.I16 -> CodeBlock.of("buf.readShort()")
    SerialTy.I32 -> CodeBlock.of("buf.readInt()")
    SerialTy.I64 -> CodeBlock.of("buf.readLong()")
    SerialTy.U8 -> CodeBlock.of("buf.readByte().toUByte()")
    SerialTy.U16 -> CodeBlock.of("buf.readShort().toUShort()")
    SerialTy.U32 -> CodeBlock.of("buf.readInt().toUInt()")
    SerialTy.U64 -> CodeBlock.of("buf.readLong().toULong()")
    SerialTy.VarU32 -> CodeBlock.of("buf.readVarInt().toUInt()")
    SerialTy.VarU64 -> CodeBlock.of("buf.readVarLong().toULong()")
    SerialTy.VarInt -> CodeBlock.of("buf.readVarInt()")
    SerialTy.VarLong -> CodeBlock.of("buf.readVarLong()")
    SerialTy.F32 -> CodeBlock.of("buf.readFloat()")
    SerialTy.F64 -> CodeBlock.of("buf.readDouble()")
    SerialTy.Str -> CodeBlock.of("buf.readUtf()")
    SerialTy.Bytes -> CodeBlock.of("buf.readByteArray()")
    SerialTy.Ints -> CodeBlock.of("IntArray(buf.readVarInt()) { buf.readInt() }")
    SerialTy.Longs -> CodeBlock.of("LongArray(buf.readVarInt()) { buf.readLong() }")
    SerialTy.BigInt, SerialTy.VarBigInt -> CodeBlock.of("%T(buf.readByteArray())", BIG_INTEGER)
    SerialTy.Uuid -> CodeBlock.of("buf.readUUID()")
    SerialTy.ResLoc -> CodeBlock.of("%T(buf.readUtf())", RESOURCE_LOCATION)
    SerialTy.BlockPos -> CodeBlock.of("%T.of(buf.readLong())", BLOCK_POS)
    is SerialTy.Enum -> if (t.ordinal) CodeBlock.of("%L.entries[buf.readUnsignedByte().toInt()]", t.name)
    else CodeBlock.of("%L.valueOf(buf.readUtf())", t.name)
    is SerialTy.Nested -> CodeBlock.of("%L.read(buf)", t.name)
    is SerialTy.ListOf -> CodeBlock.of(
        "run { val n = buf.readVarInt(); val list = ArrayList<%L>(n); repeat(n) { list.add(%L) }; list }",
        typeKt(t.element), bufRead(t.element),
    )
    is SerialTy.MapOf -> CodeBlock.of(
        "run { val n = buf.readVarInt(); val m = LinkedHashMap<%L, %L>(n); repeat(n) { val k = %L; val v = %L; m[k] = v }; m }",
        typeKt(t.key), typeKt(t.value), bufRead(t.key), bufRead(t.value),
    )
    is SerialTy.SetOf -> CodeBlock.of(
        "run { val n = buf.readVarInt(); val s = LinkedHashSet<%L>(n); repeat(n) { s.add(%L) }; s }",
        typeKt(t.element), bufRead(t.element),
    )
}

private fun readIntoFun(cls: SerialClass): FunSpec {
    val body = CodeBlock.builder()
    for (f in cls.fields) {
        body.beginControlFlow("if (tag.contains(%S))", f.wireName)
        when (val t = f.type) {
            is SerialTy.ListOf -> {
                body.addStatement("val %N = tag.getList(%S, %L)", "_${f.name}", f.wireName, listTagType(t.element))
                body.addStatement(
                    "val %N = ArrayList<%L>(%N.size)",
                    f.name, typeKt(t.element), "_${f.name}",
                )
                body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                body.addStatement("%N.add(%L)", f.name, listGet(t.element, "_${f.name}"))
                body.endControlFlow()
                body.addStatement("this.%N = %N", f.name, f.name)
            }
            is SerialTy.SetOf -> {
                body.addStatement("val %N = tag.getList(%S, %L)", "_${f.name}", f.wireName, listTagType(t.element))
                body.addStatement(
                    "val %N = LinkedHashSet<%L>(%N.size)",
                    f.name, typeKt(t.element), "_${f.name}",
                )
                body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                body.addStatement("%N.add(%L)", f.name, listGet(t.element, "_${f.name}"))
                body.endControlFlow()
                body.addStatement("this.%N = %N", f.name, f.name)
            }
            is SerialTy.MapOf -> {
                if (t.key == SerialTy.Str) {
                    body.addStatement("val %N = tag.getCompound(%S)", "_${f.name}", f.wireName)
                    body.addStatement(
                        "val %N = LinkedHashMap<%L, %L>(%N.allKeys.size)",
                        f.name, typeKt(t.key), typeKt(t.value), "_${f.name}",
                    )
                    body.beginControlFlow("for (k in %N.allKeys)", "_${f.name}")
                    body.addStatement("val __v = %L", nbtGetValueDynamic(t.value, "_${f.name}", "k"))
                    body.addStatement("%N[k] = __v", f.name)
                    body.endControlFlow()
                    body.addStatement("this.%N = %N", f.name, f.name)
                } else {
                    body.addStatement(
                        "val %N = tag.getList(%S, %T.TAG_COMPOUND.toInt())",
                        "_${f.name}", f.wireName, TAG,
                    )
                    body.addStatement(
                        "val %N = LinkedHashMap<%L, %L>(%N.size)",
                        f.name, typeKt(t.key), typeKt(t.value), "_${f.name}",
                    )
                    body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                    body.addStatement("val __e = %N.getCompound(i)", "_${f.name}")
                    body.addStatement("val __k = %L", nbtGetValue(t.key, "__e", "k"))
                    body.addStatement("val __v = %L", nbtGetValue(t.value, "__e", "v"))
                    body.addStatement("%N[__k] = __v", f.name)
                    body.endControlFlow()
                    body.addStatement("this.%N = %N", f.name, f.name)
                }
            }
            else -> body.addStatement("this.%N = %L", f.name, nbtRead(f.type, f.wireName))
        }
        body.endControlFlow()
    }
    return FunSpec.builder("readInto")
        .receiver(ClassName(cls.pkg, cls.name))
        .addParameter("tag", COMPOUND_TAG)
        .addCode(body.build())
        .build()
}
