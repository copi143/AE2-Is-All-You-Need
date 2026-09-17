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

private fun collectImports(cls: SerialClass): Set<Pair<String, String>> = cls.fields.flatMap { it.type.imports }.toSet()

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
    body: CodeBlock.Builder, tagVar: String, key: String, acc: String, t: SerialType, keyIsVar: Boolean
) = t.emitNbtPut(body, tagVar, key, acc, keyIsVar)

private fun nbtPut(body: CodeBlock.Builder, f: SerialProp, acc: String) {
    if (f.type !is SerialType.Container) {
        nbtPutSingle(body, "tag", f.wireName, acc, f.type, false); return
    }
    when (val t = f.type) {
        is SerialType.ListOf -> {
            body.beginControlFlow("run")
            body.addStatement("val __listTag = %T()", Clazz.ListTag)
            body.beginControlFlow("for (item in %L)", acc)
            putListElement(body, "__listTag", "item", t.element)
            body.endControlFlow()
            body.addStatement("tag.put(%S, __listTag)", f.wireName)
            body.endControlFlow()
        }

        is SerialType.MapOf -> {
            if (t.key == SerialType.Str) {
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

        is SerialType.SetOf -> {
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

private fun putListElement(body: CodeBlock.Builder, listVar: String, itemVar: String, elem: SerialType) =
    elem.emitListAdd(body, listVar, itemVar)

private fun nbtPutValue(body: CodeBlock.Builder, tagVar: String, key: String, acc: String, type: SerialType) {
    if (type !is SerialType.Container) {
        nbtPutSingle(body, tagVar, key, acc, type, false); return
    }
    when (type) {
        is SerialType.ListOf -> {
            body.addStatement("val __list = %T()", Clazz.ListTag)
            body.beginControlFlow("for (__item in %L)", acc)
            putListElement(body, "__list", "__item", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%S, __list)", tagVar, key)
        }

        is SerialType.MapOf -> {
            if (type.key == SerialType.Str) {
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

        is SerialType.SetOf -> {
            body.addStatement("val __s = %T()", Clazz.ListTag)
            body.beginControlFlow("for (__item in %L)", acc)
            putListElement(body, "__s", "__item", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%S, __s)", tagVar, key)
        }
    }
}

private fun nbtPutValueDynamic(body: CodeBlock.Builder, tagVar: String, keyVar: String, acc: String, type: SerialType) {
    if (type !is SerialType.Container) {
        nbtPutSingle(body, tagVar, keyVar, acc, type, true); return
    }
    when (type) {
        is SerialType.ListOf -> {
            body.addStatement("val __ld = %T()", Clazz.ListTag)
            body.beginControlFlow("for (__it in %L)", acc)
            putListElement(body, "__ld", "__it", type.element)
            body.endControlFlow()
            body.addStatement("%L.put(%L, __ld)", tagVar, keyVar)
        }

        is SerialType.MapOf, is SerialType.SetOf -> nbtPutValue(body, tagVar, keyVar, acc, type)
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
                    is SerialType.ListOf -> {
                        body.addStatement(
                            """
                                val %N = if (tag.contains(%S)) run {
                                    val __l = tag.getList(%S, %L)
                                    val __r = ArrayList<%L>(__l.size)
                                    for (i in __l.indices) {
                                        __r.add(%L)
                                    }
                                    __r
                                } else null
                            """.trimIndent(),
                            f.name,
                            f.wireName,
                            f.wireName,
                            t.element.listTag(),
                            t.element.shortType,
                            listGet(t.element, "__l"),
                        )
                    }

                    is SerialType.SetOf -> {
                        body.addStatement(
                            """
                                val %N = if (tag.contains(%S)) run {
                                    val __l = tag.getList(%S, %L)
                                    val __r = LinkedHashSet<%L>(__l.size)
                                    for (i in __l.indices) {
                                        __r.add(%L)
                                    }
                                    __r
                                } else null
                            """.trimIndent(),
                            f.name,
                            f.wireName,
                            f.wireName,
                            t.element.listTag(),
                            t.element.shortType,
                            listGet(t.element, "__l"),
                        )
                    }

                    is SerialType.MapOf -> if (t.key == SerialType.Str) {
                        body.addStatement(
                            """
                                val %N = if (tag.contains(%S)) run {
                                    val __m = tag.getCompound(%S)
                                    val __r = LinkedHashMap<%L, %L>(__m.allKeys.size)
                                    for (__k in __m.allKeys) {
                                        __r[__k] = %L
                                    }
                                    __r
                                } else null
                            """.trimIndent(),
                            f.name,
                            f.wireName,
                            f.wireName,
                            t.key.shortType,
                            t.value.shortType,
                            nbtGetValueDynamic(t.value, "__m", "__k"),
                        )
                    } else {
                        body.addStatement(
                            """
                                val %N = if (tag.contains(%S)) run {
                                    val __l = tag.getList(%S, %T.TAG_COMPOUND.toInt())
                                    val __r = LinkedHashMap<%L, %L>(__l.size)
                                    for (i in __l.indices) {
                                        val __e = __l.getCompound(i)
                                        val __k = %L
                                        val __v = %L
                                        __r[__k] = __v
                                    }
                                    __r
                                } else null
                            """.trimIndent(),
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
                is SerialType.ListOf -> {
                    body.addStatement(
                        "val %N = tag.getList(%S, %L)",
                        "_${f.name}", f.wireName, t.element.listTag(),
                    )
                    body.addStatement(
                        "val %N = ArrayList<%L>(%N.size)",
                        f.name, t.element.shortType, "_${f.name}",
                    )
                    body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                    body.addStatement("%N.add(%L)", f.name, listGet(t.element, "_${f.name}"))
                    body.endControlFlow()
                }

                is SerialType.SetOf -> {
                    body.addStatement(
                        "val %N = tag.getList(%S, %L)",
                        "_${f.name}", f.wireName, t.element.listTag(),
                    )
                    body.addStatement(
                        "val %N = LinkedHashSet<%L>(%N.size)",
                        f.name, t.element.shortType, "_${f.name}",
                    )
                    body.beginControlFlow("for (i in %N.indices)", "_${f.name}")
                    body.addStatement("%N.add(%L)", f.name, listGet(t.element, "_${f.name}"))
                    body.endControlFlow()
                }

                is SerialType.MapOf -> {
                    if (t.key == SerialType.Str) {
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

private fun nbtGetSingle(t: SerialType, tagVar: String, key: String, keyIsVar: Boolean): CodeBlock =
    t.nbtGet(tagVar, key, keyIsVar)

private fun nbtRead(t: SerialType, k: String): CodeBlock = nbtGetValue(t, "tag", k)

private fun nbtGetValue(t: SerialType, tagVar: String, k: String): CodeBlock {
    if (t is SerialType.Container) error("collection handled separately")
    return nbtGetSingle(t, tagVar, k, false)
}

private fun nbtGetValueDynamic(t: SerialType, tagVar: String, keyVar: String): CodeBlock {
    if (t !is SerialType.Container) {
        return nbtGetSingle(t, tagVar, keyVar, true)
    }
    return when (t) {
        is SerialType.ListOf -> CodeBlock.of(
            "run { val __l = %L.getList(%L, %L); val __r = ArrayList<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r }",
            tagVar, keyVar, t.element.listTag(), t.element.shortType, listGet(t.element, "__l"),
        )

        is SerialType.SetOf -> CodeBlock.of(
            "run { val __l = %L.getList(%L, %L); val __r = LinkedHashSet<%L>(__l.size); for (i in __l.indices) __r.add(%L); __r }",
            tagVar, keyVar, t.element.listTag(), t.element.shortType, listGet(t.element, "__l"),
        )

        is SerialType.MapOf -> if (t.key == SerialType.Str) CodeBlock.of(
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

private fun listTagType(elem: SerialType): CodeBlock = elem.listTag()

private fun listGet(elem: SerialType, list: String): CodeBlock = elem.listGet(list)

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

private fun bufWrite(body: CodeBlock.Builder, t: SerialType, acc: String) {
    if (t !is SerialType.Container) {
        t.emitBufWrite(body, acc); return
    }
    when (t) {
        is SerialType.ListOf -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.beginControlFlow("for (item in %L)", acc)
            bufWrite(body, t.element, "item")
            body.endControlFlow()
        }

        is SerialType.MapOf -> {
            body.addStatement("buf.writeVarInt(%L.size)", acc)
            body.beginControlFlow("for ((k, v) in %L)", acc)
            bufWrite(body, t.key, "k")
            bufWrite(body, t.value, "v")
            body.endControlFlow()
        }

        is SerialType.SetOf -> {
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
                is SerialType.MapOf -> {
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

                is SerialType.SetOf -> {
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

private fun bufRead(t: SerialType): CodeBlock {
    if (t !is SerialType.Container) return t.bufRead()
    return when (t) {
        is SerialType.ListOf -> CodeBlock.of(
            "run { val n = buf.readVarInt(); val list = ArrayList<%L>(n); repeat(n) { list.add(%L) }; list }",
            t.element.shortType, bufRead(t.element),
        )

        is SerialType.MapOf -> CodeBlock.of(
            "run { val n = buf.readVarInt(); val m = LinkedHashMap<%L, %L>(n); repeat(n) { val k = %L; val v = %L; m[k] = v }; m }",
            t.key.shortType, t.value.shortType, bufRead(t.key), bufRead(t.value),
        )

        is SerialType.SetOf -> CodeBlock.of(
            "run { val n = buf.readVarInt(); val s = LinkedHashSet<%L>(n); repeat(n) { s.add(%L) }; s }",
            t.element.shortType, bufRead(t.element),
        )
    }
}

private fun readIntoFun(cls: SerialClass): FunSpec {
    val body = CodeBlock.builder()
    for (f in cls.fields) {
        body.beginControlFlow("if (tag.contains(%S))", f.wireName)
        when (val t = f.type) {
            is SerialType.ListOf -> {
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

            is SerialType.SetOf -> {
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

            is SerialType.MapOf -> {
                if (t.key == SerialType.Str) {
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
