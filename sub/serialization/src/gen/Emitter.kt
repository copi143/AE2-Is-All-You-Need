package io.github.copi143.serialization.gen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec

fun emit(cls: SerialClass): FileSpec {
    val file = FileSpec.builder(cls.pkg, "${cls.name}Serdes")
    for ((pkg, name) in collectImports(cls)) file.addImport(pkg, name)
    file.addFunction(toNbtFun(cls))
    file.addFunction(writeFun(cls))
    if (!cls.construct) file.addFunction(readIntoFun(cls))
    file.addFunction(fromNbtFun(cls))
    file.addFunction(readFun(cls))
    return file.build()
}

private fun collectImports(cls: SerialClass): Set<Pair<String, String>> {
    val out = mutableSetOf<Pair<String, String>>()
    fun walk(t: SerialTy) {
        when (t) {
            SerialTy.UUID -> out.add("java.util" to "UUID")
            SerialTy.BigInt, SerialTy.VarBigInt -> out.add("java.math" to "BigInteger")
            SerialTy.ResLoc -> out.add("net.minecraft.resources" to "ResourceLocation")
            SerialTy.BlockPos -> out.add("net.minecraft.core" to "BlockPos")
            is SerialTy.ListOf -> walk(t.element)
            is SerialTy.MapOf -> {
                walk(t.key); walk(t.value)
            }

            is SerialTy.SetOf -> walk(t.element)
            else -> {}
        }
    }
    for (f in cls.fields) walk(f.type)
    return out
}

private fun toNbtFun(cls: SerialClass): FunSpec {
    val body = CodeBlock.builder()
    body.addStatement("val tag = %T()", Clazz.CompoundTag)
    for (f in cls.fields) {
        if (f.nullable) {
            body.beginControlFlow("if (%N != null)", f.name)
            nbtPut(body, f, "${f.name}!!")
            body.endControlFlow()
        } else nbtPut(body, f, f.name)
    }
    body.addStatement("return tag")
    return FunSpec.builder("toNbt").receiver(ClassName(cls.pkg, cls.name)).returns(Clazz.CompoundTag)
        .addCode(body.build()).build()
}

private fun nbtPutSingle(
    body: CodeBlock.Builder, tagVar: String, key: String, acc: String, t: SerialTy, keyIsVar: Boolean
) {
    val kFmt = if (keyIsVar) "%L" else "%S"
    when (t) {
        SerialTy.Bool -> body.addStatement("$tagVar.putBoolean($kFmt, %L)", key, acc)
        SerialTy.I8 -> body.addStatement("$tagVar.putByte($kFmt, %L)", key, acc)
        SerialTy.I16 -> body.addStatement("$tagVar.putShort($kFmt, %L)", key, acc)
        SerialTy.I32, SerialTy.VarI32 -> body.addStatement("$tagVar.putInt($kFmt, %L)", key, acc)
        SerialTy.I64, SerialTy.VarI64 -> body.addStatement("$tagVar.putLong($kFmt, %L)", key, acc)
        SerialTy.U8 -> body.addStatement("$tagVar.putByte($kFmt, %L.toByte())", key, acc)
        SerialTy.U16 -> body.addStatement("$tagVar.putShort($kFmt, %L.toShort())", key, acc)
        SerialTy.U32, SerialTy.VarU32 -> body.addStatement("$tagVar.putInt($kFmt, %L.toInt())", key, acc)
        SerialTy.U64, SerialTy.VarU64 -> body.addStatement("$tagVar.putLong($kFmt, %L.toLong())", key, acc)
        SerialTy.F32 -> body.addStatement("$tagVar.putFloat($kFmt, %L)", key, acc)
        SerialTy.F64 -> body.addStatement("$tagVar.putDouble($kFmt, %L)", key, acc)
        SerialTy.Str -> body.addStatement("$tagVar.putString($kFmt, %L)", key, acc)
        SerialTy.I8Array -> body.addStatement("$tagVar.putByteArray($kFmt, %L)", key, acc)
        SerialTy.I32Array -> body.addStatement("$tagVar.putIntArray($kFmt, %L)", key, acc)
        SerialTy.I64Array -> body.addStatement("$tagVar.putLongArray($kFmt, %L)", key, acc)
        SerialTy.BigInt, SerialTy.VarBigInt -> body.addStatement(
            "$tagVar.putByteArray($kFmt, %L.toByteArray())", key, acc
        )

        SerialTy.UUID -> body.addStatement("$tagVar.putUUID($kFmt, %L)", key, acc)
        SerialTy.ResLoc -> body.addStatement("$tagVar.putString($kFmt, %L.toString())", key, acc)
        SerialTy.BlockPos -> body.addStatement("$tagVar.putLong($kFmt, %L.asLong())", key, acc)
        is SerialTy.Enum -> if (t.ordinal) body.addStatement(
            "$tagVar.putByte($kFmt, %L.ordinal.toByte())", key, acc
        ) else body.addStatement("$tagVar.putString($kFmt, %L.name)", key, acc)

        is SerialTy.Nested -> body.addStatement("$tagVar.put($kFmt, %L.toNbt())", key, acc)
        else -> error("collection handled separately")
    }
}

private fun nbtPut(body: CodeBlock.Builder, f: SerialProp, acc: String) {
    when (val t = f.type) {
        is SerialTy.ListOf, is SerialTy.MapOf, is SerialTy.SetOf -> {} // handled below
        else -> {
            nbtPutSingle(body, "tag", f.wireName, acc, t, false); return
        }
    }
    when (val t = f.type) {
        is SerialTy.ListOf -> {
            body.beginControlFlow("run")
            body.addStatement("val __listTag = %T()", Clazz.ListTag)
            body.beginControlFlow("for (item in %L)", acc)
            putListElement(body, "__listTag", "item", t.element)
            body.endControlFlow()
            body.addStatement("tag.put(%S, __listTag)", f.wireName)
            body.endControlFlow()
        }

        is SerialTy.MapOf -> {
            if (t.key == SerialTy.Str) {
                body.beginControlFlow("run")
                body.addStatement("val mapTag = %T()", Clazz.CompoundTag)
                body.beginControlFlow("for ((k, v) in %L)", acc)
                nbtPutValueDynamic(body, "mapTag", "k", "v", t.value)
                body.endControlFlow()
                body.addStatement("tag.put(%S, mapTag)", f.wireName)
                body.endControlFlow()
            } else {
                body.beginControlFlow("run")
                body.addStatement("val __listTag = %T()", Clazz.ListTag)
                body.beginControlFlow("for ((k, v) in %L)", acc)
                body.addStatement("val entry = %T()", Clazz.CompoundTag)
                nbtPutValue(body, "entry", "k", "k", t.key)
                nbtPutValue(body, "entry", "v", "v", t.value)
                body.addStatement("__listTag.add(entry)")
                body.endControlFlow()
                body.addStatement("tag.put(%S, __listTag)", f.wireName)
                body.endControlFlow()
            }
        }

        is SerialTy.SetOf -> {
            body.beginControlFlow("run")
            body.addStatement("val __listTag = %T()", Clazz.ListTag)
            body.beginControlFlow("for (item in %L)", acc)
            putListElement(body, "__listTag", "item", t.element)
            body.endControlFlow()
            body.addStatement("tag.put(%S, __listTag)", f.wireName)
            body.endControlFlow()
        }
    }
}

private fun putListElement(body: CodeBlock.Builder, listVar: String, itemVar: String, elem: SerialTy) {
    when (elem) {
        SerialTy.Str -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, Clazz.StringTag, itemVar)
        SerialTy.I32 -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, Clazz.IntTag, itemVar)
        SerialTy.I64 -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, Clazz.LongTag, itemVar)
        SerialTy.U8 -> body.addStatement("%L.add(%T.valueOf(%L.toByte()))", listVar, Clazz.ByteTag, itemVar)
        SerialTy.U16 -> body.addStatement("%L.add(%T.valueOf(%L.toShort()))", listVar, Clazz.ShortTag, itemVar)
        SerialTy.U32 -> body.addStatement("%L.add(%T.valueOf(%L.toInt()))", listVar, Clazz.IntTag, itemVar)
        SerialTy.U64 -> body.addStatement("%L.add(%T.valueOf(%L.toLong()))", listVar, Clazz.LongTag, itemVar)
        SerialTy.Bool -> body.addStatement("%L.add(%T.valueOf(%L))", listVar, Clazz.ByteTag, itemVar)
        is SerialTy.Nested -> body.addStatement("%L.add(%L.toNbt())", listVar, itemVar)
        is SerialTy.Enum -> if (elem.ordinal) body.addStatement(
            "%L.add(%T.valueOf(%L.ordinal.toByte()))", listVar, Clazz.ByteTag, itemVar
        )
        else body.addStatement("%L.add(%T.valueOf(%L.name))", listVar, Clazz.StringTag, itemVar)

        is SerialTy.ListOf -> {
            body.addStatement("val __inner = %T()", Clazz.ListTag)
            body.beginControlFlow("for (__e in %L)", itemVar)
            putListElement(body, "__inner", "__e", elem.element)
            body.endControlFlow()
            body.addStatement("%L.add(__inner)", listVar)
        }

        is SerialTy.MapOf, is SerialTy.SetOf -> body.addStatement("%L.add(%L.toNbtTag())", listVar, itemVar)
        else -> body.addStatement("%L.add(%T.valueOf(%L.toString()))", listVar, Clazz.StringTag, itemVar)
    }
}

private fun nbtPutValue(body: CodeBlock.Builder, tagVar: String, key: String, acc: String, type: SerialTy) {
    when (type) {
        is SerialTy.ListOf, is SerialTy.MapOf, is SerialTy.SetOf -> {}
        else -> {
            nbtPutSingle(body, tagVar, key, acc, type, false); return
        }
    }
    when (type) {
        is SerialTy.ListOf -> {
            body.addStatement("val __list = %T()", Clazz.ListTag)
            body.beginControlFlow("for (__item in %L)", acc)
            putListElement(body, "__list", "__item", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%S, __list)", tagVar, key)
        }

        is SerialTy.MapOf -> {
            if (type.key == SerialTy.Str) {
                body.addStatement("val __m = %T()", Clazz.CompoundTag)
                body.beginControlFlow("for ((__k, __v) in %L)", acc)
                nbtPutValueDynamic(body, "__m", "__k", "__v", type.value)
                body.endControlFlow()
                body.addStatement("%L.put(%S, __m)", tagVar, key)
            } else {
                body.addStatement("val __ml = %T()", Clazz.ListTag)
                body.beginControlFlow("for ((__k, __v) in %L)", acc)
                body.addStatement("val __e = %T()", Clazz.CompoundTag)
                nbtPutValue(body, "__e", "k", "__k", type.key)
                nbtPutValue(body, "__e", "v", "__v", type.value)
                body.addStatement("__ml.add(__e)")
                body.endControlFlow()
                body.addStatement("%L.put(%S, __ml)", tagVar, key)
            }
        }

        is SerialTy.SetOf -> {
            body.addStatement("val __s = %T()", Clazz.ListTag)
            body.beginControlFlow("for (__item in %L)", acc)
            putListElement(body, "__s", "__item", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%S, __s)", tagVar, key)
        }
    }
}

private fun nbtPutValueDynamic(body: CodeBlock.Builder, tagVar: String, keyVar: String, acc: String, type: SerialTy) {
    when (type) {
        is SerialTy.ListOf, is SerialTy.MapOf, is SerialTy.SetOf -> {}
        else -> {
            nbtPutSingle(body, tagVar, keyVar, acc, type, true); return
        }
    }
    when (type) {
        is SerialTy.ListOf -> {
            body.addStatement("val __ld = %T()", Clazz.ListTag)
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
                when (val t = f.type) {
                    is SerialTy.ListOf -> {
                        body.addStatement(
                            "val %N = if (tag.contains(%S)) run { val __l = tag.getList(%S, %L); val __r = ArrayList<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r } else null",
                            f.name,
                            f.wireName,
                            f.wireName,
                            listTagType(t.element),
                            t.element.shortType,
                            listGet(t.element, "__l"),
                        )
                    }

                    is SerialTy.SetOf -> {
                        body.addStatement(
                            "val %N = if (tag.contains(%S)) run { val __l = tag.getList(%S, %L); val __r = LinkedHashSet<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r } else null",
                            f.name,
                            f.wireName,
                            f.wireName,
                            listTagType(t.element),
                            t.element.shortType,
                            listGet(t.element, "__l"),
                        )
                    }

                    is SerialTy.MapOf -> if (t.key == SerialTy.Str) {
                        body.addStatement(
                            "val %N = if (tag.contains(%S)) run { val __m = tag.getCompound(%S); val __r = LinkedHashMap<%L, %L>(__m.allKeys.size); for (__k in __m.allKeys) __r[__k] = %L; __r } else null",
                            f.name,
                            f.wireName,
                            f.wireName,
                            t.key.shortType,
                            t.value.shortType,
                            nbtGetValueDynamic(t.value, "__m", "__k"),
                        )
                    } else {
                        body.addStatement(
                            "val %N = if (tag.contains(%S)) run { val __l = tag.getList(%S, %T.TAG_COMPOUND.toInt()); val __r = LinkedHashMap<%L, %L>(__l.size); for (i in __l.indices) { val __e = __l.getCompound(i); val __k = %L; val __v = %L; __r[__k] = __v }; __r } else null",
                            f.name,
                            f.wireName,
                            f.wireName,
                            Clazz.Tag,
                            t.key.shortType,
                            t.value.shortType,
                            nbtGetValue(t.key, "__e", "k"),
                            nbtGetValue(t.value, "__e", "v"),
                        )
                    }

                    else -> body.addStatement(
                        "val %N = if (tag.contains(%S)) %L else null",
                        f.name, f.wireName, nbtRead(t, f.wireName),
                    )
                }
            } else when (val t = f.type) {
                is SerialTy.ListOf -> {
                    body.addStatement(
                        "val %N = tag.getList(%S, %L)",
                        "_${f.name}", f.wireName, listTagType(t.element),
                    )
                    body.addStatement(
                        "val %N = ArrayList<%L>(%N.size)",
                        f.name, t.element.shortType, "_${f.name}",
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
                        f.name, t.element.shortType, "_${f.name}",
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
                            f.name, t.key.shortType, t.value.shortType, "_${f.name}",
                        )
                        body.beginControlFlow("for (k in %N.allKeys)", "_${f.name}")
                        body.addStatement("val __v = %L", nbtGetValueDynamic(t.value, "_${f.name}", "k"))
                        body.addStatement("%N[k] = __v", f.name)
                        body.endControlFlow()
                    } else {
                        body.addStatement(
                            "val %N = tag.getList(%S, %T.TAG_COMPOUND.toInt())",
                            "_${f.name}", f.wireName, Clazz.Tag,
                        )
                        body.addStatement(
                            "val %N = LinkedHashMap<%L, %L>(%N.size)",
                            f.name, t.key.shortType, t.value.shortType, "_${f.name}",
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
    return FunSpec.builder("fromNbt").receiver(self.nestedClass("Companion")).addParameter("tag", Clazz.CompoundTag)
        .returns(self).addCode(body.build()).build()
}

private fun nbtGetSingle(t: SerialTy, tagVar: String, key: String, keyIsVar: Boolean): CodeBlock {
    val kFmt = if (keyIsVar) "%L" else "%S"
    return when (t) {
        SerialTy.Bool -> CodeBlock.of("$tagVar.getBoolean($kFmt)", key)
        SerialTy.I8 -> CodeBlock.of("$tagVar.getByte($kFmt)", key)
        SerialTy.I16 -> CodeBlock.of("$tagVar.getShort($kFmt)", key)
        SerialTy.I32, SerialTy.VarI32 -> CodeBlock.of("$tagVar.getInt($kFmt)", key)
        SerialTy.I64, SerialTy.VarI64 -> CodeBlock.of("$tagVar.getLong($kFmt)", key)
        SerialTy.U8 -> CodeBlock.of("$tagVar.getByte($kFmt).toUByte()", key)
        SerialTy.U16 -> CodeBlock.of("$tagVar.getShort($kFmt).toUShort()", key)
        SerialTy.U32, SerialTy.VarU32 -> CodeBlock.of("$tagVar.getInt($kFmt).toUInt()", key)
        SerialTy.U64, SerialTy.VarU64 -> CodeBlock.of("$tagVar.getLong($kFmt).toULong()", key)
        SerialTy.F32 -> CodeBlock.of("$tagVar.getFloat($kFmt)", key)
        SerialTy.F64 -> CodeBlock.of("$tagVar.getDouble($kFmt)", key)
        SerialTy.Str -> CodeBlock.of("$tagVar.getString($kFmt)", key)
        SerialTy.I8Array -> CodeBlock.of("$tagVar.getByteArray($kFmt)", key)
        SerialTy.I32Array -> CodeBlock.of("$tagVar.getIntArray($kFmt)", key)
        SerialTy.I64Array -> CodeBlock.of("$tagVar.getLongArray($kFmt)", key)
        SerialTy.BigInt, SerialTy.VarBigInt -> CodeBlock.of("%T($tagVar.getByteArray($kFmt))", Clazz.BigInteger, key)
        SerialTy.UUID -> CodeBlock.of("$tagVar.getUUID($kFmt)", key)
        SerialTy.ResLoc -> CodeBlock.of("%T($tagVar.getString($kFmt))", Clazz.ResourceLocation, key)
        SerialTy.BlockPos -> CodeBlock.of("%T.of($tagVar.getLong($kFmt))", Clazz.BlockPos, key)
        is SerialTy.Enum -> if (t.ordinal) CodeBlock.of(
            "%L.entries[$tagVar.getByte($kFmt).toInt() and 0xFF]", t.name, key
        ) else CodeBlock.of("%L.valueOf($tagVar.getString($kFmt))", t.name, key)

        is SerialTy.Nested -> CodeBlock.of("%L.fromNbt($tagVar.getCompound($kFmt))", t.name, key)
        else -> error("collection handled separately")
    }
}

private fun nbtRead(t: SerialTy, k: String): CodeBlock = nbtGetValue(t, "tag", k)

private fun nbtGetValue(t: SerialTy, tagVar: String, k: String): CodeBlock {
    if (t is SerialTy.ListOf || t is SerialTy.MapOf || t is SerialTy.SetOf) error("collection handled separately")
    return nbtGetSingle(t, tagVar, k, false)
}

private fun nbtGetValueDynamic(t: SerialTy, tagVar: String, keyVar: String): CodeBlock {
    if (t is SerialTy.ListOf || t is SerialTy.MapOf || t is SerialTy.SetOf) {
        // handled below
    } else {
        return nbtGetSingle(t, tagVar, keyVar, true)
    }
    return when (t) {
        is SerialTy.ListOf -> CodeBlock.of(
            "run { val __l = %L.getList(%L, %L); val __r = ArrayList<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r }",
            tagVar, keyVar, listTagType(t.element), t.element.shortType, listGet(t.element, "__l"),
        )

        is SerialTy.SetOf -> CodeBlock.of(
            "run { val __l = %L.getList(%L, %L); val __r = LinkedHashSet<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r }",
            tagVar, keyVar, listTagType(t.element), t.element.shortType, listGet(t.element, "__l"),
        )

        is SerialTy.MapOf -> if (t.key == SerialTy.Str) CodeBlock.of(
            "run { val __m = %L.getCompound(%L); val __r = LinkedHashMap<%L, %L>(__m.allKeys.size); for (__kk in __m.allKeys) __r[__kk] = %L; __r }",
            tagVar, keyVar, t.key.shortType, t.value.shortType, nbtGetValueDynamic(t.value, "__m", "__kk"),
        ) else CodeBlock.of(
            "run { val __l = %L.getList(%L, %T.TAG_COMPOUND.toInt()); val __r = LinkedHashMap<%L, %L>(__l.size); for (i in __l.indices) { val __e = __l.getCompound(i); val __k = %L; val __v = %L; __r[__k] = __v }; __r }",
            tagVar,
            keyVar,
            Clazz.Tag,
            t.key.shortType,
            t.value.shortType,
            nbtGetValue(t.key, "__e", "k"),
            nbtGetValue(t.value, "__e", "v"),
        )
    }
}

private fun listTagType(elem: SerialTy): CodeBlock = when (elem) {
    SerialTy.Str -> CodeBlock.of("%T.TAG_STRING.toInt()", Clazz.Tag)
    SerialTy.I32 -> CodeBlock.of("%T.TAG_INT.toInt()", Clazz.Tag)
    SerialTy.I64 -> CodeBlock.of("%T.TAG_LONG.toInt()", Clazz.Tag)
    SerialTy.I8, SerialTy.Bool, SerialTy.U8 -> CodeBlock.of("%T.TAG_BYTE.toInt()", Clazz.Tag)
    SerialTy.U16 -> CodeBlock.of("%T.TAG_SHORT.toInt()", Clazz.Tag)
    SerialTy.U32 -> CodeBlock.of("%T.TAG_INT.toInt()", Clazz.Tag)
    SerialTy.U64 -> CodeBlock.of("%T.TAG_LONG.toInt()", Clazz.Tag)
    is SerialTy.Nested -> CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)
    is SerialTy.Enum -> if (elem.ordinal) CodeBlock.of("%T.TAG_BYTE.toInt()", Clazz.Tag)
    else CodeBlock.of("%T.TAG_STRING.toInt()", Clazz.Tag)

    else -> CodeBlock.of("%T.TAG_COMPOUND.toInt()", Clazz.Tag)
}

private fun listGet(elem: SerialTy, list: String): CodeBlock = when (elem) {
    SerialTy.Str -> CodeBlock.of("%L.getString(i)", list)
    SerialTy.I32 -> CodeBlock.of("(%L.get(i) as %T).getAsInt()", list, Clazz.NumericTag)
    SerialTy.I64 -> CodeBlock.of("(%L.get(i) as %T).getAsLong()", list, Clazz.NumericTag)
    SerialTy.Bool -> CodeBlock.of("(%L.get(i) as %T).getAsByte().toInt() != 0", list, Clazz.NumericTag)
    SerialTy.U8 -> CodeBlock.of("(%L.get(i) as %T).getAsByte().toUByte()", list, Clazz.NumericTag)
    SerialTy.U16 -> CodeBlock.of("(%L.get(i) as %T).getAsShort().toUShort()", list, Clazz.NumericTag)
    SerialTy.U32 -> CodeBlock.of("(%L.get(i) as %T).getAsInt().toUInt()", list, Clazz.NumericTag)
    SerialTy.U64 -> CodeBlock.of("(%L.get(i) as %T).getAsLong().toULong()", list, Clazz.NumericTag)
    is SerialTy.Nested -> CodeBlock.of("%L.fromNbt(%L.getCompound(i))", elem.name, list)
    is SerialTy.Enum -> if (elem.ordinal) CodeBlock.of(
        "%L.entries[(%L.get(i) as %T).getAsByte().toInt() and 0xFF]",
        elem.name, list, Clazz.NumericTag,
    ) else CodeBlock.of("%L.valueOf(%L.getString(i))", elem.name, list)

    else -> error("unsupported $elem")
}

private fun writeFun(cls: SerialClass): FunSpec {
    val body = CodeBlock.builder()
    for ((name, _, _, _, type, nullable) in cls.fields) {
        if (nullable) {
            body.addStatement("buf.writeBoolean(%N != null)", name)
            body.beginControlFlow("if (%N != null)", name)
            bufWrite(body, type, "$name!!")
            body.endControlFlow()
        } else bufWrite(body, type, name)
    }
    return FunSpec.builder("write").receiver(ClassName(cls.pkg, cls.name)).addParameter("buf", Clazz.FriendlyByteBuf)
        .addCode(body.build()).build()
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
        SerialTy.VarI32 -> body.addStatement("buf.writeVarInt(%L)", acc)
        SerialTy.VarI64 -> body.addStatement("buf.writeVarLong(%L)", acc)
        SerialTy.F32 -> body.addStatement("buf.writeFloat(%L)", acc)
        SerialTy.F64 -> body.addStatement("buf.writeDouble(%L)", acc)
        SerialTy.Str -> body.addStatement("buf.writeUtf(%L)", acc)
        SerialTy.I8Array -> body.addStatement("buf.writeByteArray(%L)", acc)
        SerialTy.I32Array -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.addStatement("for (n in %L) buf.writeInt(n)", acc)
        }

        SerialTy.I64Array -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.addStatement("for (n in %L) buf.writeLong(n)", acc)
        }

        SerialTy.BigInt, SerialTy.VarBigInt -> body.addStatement("buf.writeByteArray(%L.toByteArray())", acc)
        SerialTy.UUID -> body.addStatement("buf.writeUUID(%L)", acc)
        SerialTy.ResLoc -> body.addStatement("buf.writeUtf(%L.toString())", acc)
        SerialTy.BlockPos -> body.addStatement("buf.writeLong(%L.asLong())", acc)
        is SerialTy.Enum -> if (t.ordinal) body.addStatement("buf.writeByte(%L.ordinal)", acc)
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
                    body.addStatement("val __m = LinkedHashMap<%L, %L>(__n)", t.key.shortType, t.value.shortType)
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
                    body.addStatement("val __s = LinkedHashSet<%L>(__n)", t.element.shortType)
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
    return FunSpec.builder("read").receiver(self.nestedClass("Companion")).addParameter("buf", Clazz.FriendlyByteBuf)
        .returns(self).addCode(body.build()).build()
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
    SerialTy.VarI32 -> CodeBlock.of("buf.readVarInt()")
    SerialTy.VarI64 -> CodeBlock.of("buf.readVarLong()")
    SerialTy.F32 -> CodeBlock.of("buf.readFloat()")
    SerialTy.F64 -> CodeBlock.of("buf.readDouble()")
    SerialTy.Str -> CodeBlock.of("buf.readUtf()")
    SerialTy.I8Array -> CodeBlock.of("buf.readByteArray()")
    SerialTy.I32Array -> CodeBlock.of("IntArray(buf.readVarInt()) { buf.readInt() }")
    SerialTy.I64Array -> CodeBlock.of("LongArray(buf.readVarInt()) { buf.readLong() }")
    SerialTy.BigInt, SerialTy.VarBigInt -> CodeBlock.of("%T(buf.readByteArray())", Clazz.BigInteger)
    SerialTy.UUID -> CodeBlock.of("buf.readUUID()")
    SerialTy.ResLoc -> CodeBlock.of("%T(buf.readUtf())", Clazz.ResourceLocation)
    SerialTy.BlockPos -> CodeBlock.of("%T.of(buf.readLong())", Clazz.BlockPos)
    is SerialTy.Enum -> if (t.ordinal) CodeBlock.of("%L.entries[buf.readUnsignedByte().toInt()]", t.name)
    else CodeBlock.of("%L.valueOf(buf.readUtf())", t.name)

    is SerialTy.Nested -> CodeBlock.of("%L.read(buf)", t.name)
    is SerialTy.ListOf -> CodeBlock.of(
        "run { val n = buf.readVarInt(); val list = ArrayList<%L>(n); repeat(n) { list.add(%L) }; list }",
        t.element.shortType, bufRead(t.element),
    )

    is SerialTy.MapOf -> CodeBlock.of(
        "run { val n = buf.readVarInt(); val m = LinkedHashMap<%L, %L>(n); repeat(n) { val k = %L; val v = %L; m[k] = v }; m }",
        t.key.shortType, t.value.shortType, bufRead(t.key), bufRead(t.value),
    )

    is SerialTy.SetOf -> CodeBlock.of(
        "run { val n = buf.readVarInt(); val s = LinkedHashSet<%L>(n); repeat(n) { s.add(%L) }; s }",
        t.element.shortType, bufRead(t.element),
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
                    f.name, t.element.shortType, "_${f.name}",
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
                    f.name, t.element.shortType, "_${f.name}",
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
                        f.name, t.key.shortType, t.value.shortType, "_${f.name}",
                    )
                    body.beginControlFlow("for (k in %N.allKeys)", "_${f.name}")
                    body.addStatement("val __v = %L", nbtGetValueDynamic(t.value, "_${f.name}", "k"))
                    body.addStatement("%N[k] = __v", f.name)
                    body.endControlFlow()
                    body.addStatement("this.%N = %N", f.name, f.name)
                } else {
                    body.addStatement(
                        "val %N = tag.getList(%S, %T.TAG_COMPOUND.toInt())",
                        "_${f.name}", f.wireName, Clazz.Tag,
                    )
                    body.addStatement(
                        "val %N = LinkedHashMap<%L, %L>(%N.size)",
                        f.name, t.key.shortType, t.value.shortType, "_${f.name}",
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
    return FunSpec.builder("readInto").receiver(ClassName(cls.pkg, cls.name)).addParameter("tag", Clazz.CompoundTag)
        .addCode(body.build()).build()
}
