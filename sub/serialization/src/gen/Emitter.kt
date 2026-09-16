package io.github.copi143.serialization.gen

internal fun emit(cls: SerialClass): String {
    val b = StringBuilder()
    if (cls.pkg.isNotEmpty()) b.append("package ").append(cls.pkg).append("\n\n")
    b.append("import net.minecraft.nbt.CompoundTag\n")
    b.append("import net.minecraft.nbt.ListTag\n")
    b.append("import net.minecraft.nbt.StringTag\n")
    b.append("import net.minecraft.nbt.Tag\n")
    b.append("import net.minecraft.network.FriendlyByteBuf\n")
    val extra = LinkedHashSet<String>()
    fun walk(t: SerialTy) {
        when (t) {
            SerialTy.BigInt, SerialTy.VarBigInt -> extra.add("import java.math.BigInteger\n")
            SerialTy.Uuid -> extra.add("import java.util.UUID\n")
            SerialTy.ResLoc -> extra.add("import net.minecraft.resources.ResourceLocation\n")
            SerialTy.BlockPos -> extra.add("import net.minecraft.core.BlockPos\n")
            is SerialTy.ListOf -> {
                walk(t.element)
                when (t.element) {
                    SerialTy.I32 -> extra.add("import net.minecraft.nbt.IntTag\n")
                    SerialTy.I64 -> extra.add("import net.minecraft.nbt.LongTag\n")
                    SerialTy.I8, SerialTy.Bool -> extra.add("import net.minecraft.nbt.ByteTag\n")
                    else -> {}
                }
            }
            else -> {}
        }
    }
    for (f in cls.fields) walk(f.type)
    extra.sorted().forEach { b.append(it) }
    b.append('\n')
    emitToNbt(b, cls)
    emitWrite(b, cls)
    if (!cls.construct) emitReadInto(b, cls)
    emitFromNbt(b, cls)
    emitRead(b, cls)
    return b.toString()
}

private fun acc(f: SerialProp) = "this.${f.name}"

private fun emitToNbt(b: StringBuilder, cls: SerialClass) {
    b.append("fun ").append(cls.name).append(".toNbt(): CompoundTag {\n")
    b.append("    val tag = CompoundTag()\n")
    for (f in cls.fields) {
        if (f.nullable) {
            b.append("    if (${acc(f)} != null) {\n")
            emitNbtPut(b, "        ", f, "${acc(f)}!!")
            b.append("    }\n")
        } else emitNbtPut(b, "    ", f, acc(f))
    }
    b.append("    return tag\n}\n\n")
}

private fun emitNbtPut(b: StringBuilder, ind: String, f: SerialProp, acc: String) {
    when (val t = f.type) {
        SerialTy.Bool -> b.append(ind).append("tag.putBoolean(\"${f.wireName}\", $acc)\n")
        SerialTy.I8 -> b.append(ind).append("tag.putByte(\"${f.wireName}\", $acc)\n")
        SerialTy.I16 -> b.append(ind).append("tag.putShort(\"${f.wireName}\", $acc)\n")
        SerialTy.I32 -> b.append(ind).append("tag.putInt(\"${f.wireName}\", $acc)\n")
        SerialTy.I64 -> b.append(ind).append("tag.putLong(\"${f.wireName}\", $acc)\n")
        SerialTy.F32 -> b.append(ind).append("tag.putFloat(\"${f.wireName}\", $acc)\n")
        SerialTy.F64 -> b.append(ind).append("tag.putDouble(\"${f.wireName}\", $acc)\n")
        SerialTy.Str -> b.append(ind).append("tag.putString(\"${f.wireName}\", $acc)\n")
        SerialTy.Bytes -> b.append(ind).append("tag.putByteArray(\"${f.wireName}\", $acc)\n")
        SerialTy.Ints -> b.append(ind).append("tag.putIntArray(\"${f.wireName}\", $acc)\n")
        SerialTy.Longs -> b.append(ind).append("tag.putLongArray(\"${f.wireName}\", $acc)\n")
        SerialTy.BigInt, SerialTy.VarBigInt -> b.append(ind).append("tag.putByteArray(\"${f.wireName}\", $acc.toByteArray())\n")
        SerialTy.Uuid -> b.append(ind).append("tag.putUUID(\"${f.wireName}\", $acc)\n")
        SerialTy.ResLoc -> b.append(ind).append("tag.putString(\"${f.wireName}\", $acc.toString())\n")
        SerialTy.BlockPos -> b.append(ind).append("tag.putLong(\"${f.wireName}\", $acc.asLong())\n")
        is SerialTy.Enum -> if (t.ordinal) b.append(ind).append("tag.putByte(\"${f.wireName}\", $acc.ordinal.toByte())\n") else b.append(ind).append("tag.putString(\"${f.wireName}\", $acc.name)\n")
        is SerialTy.Nested -> b.append(ind).append("tag.put(\"${f.wireName}\", $acc.toNbt())\n")
        is SerialTy.ListOf -> emitNbtPutList(b, ind, f.wireName, acc, t.element)
        SerialTy.VarInt, SerialTy.VarLong, SerialTy.VarBigInt -> error("VarLen types not in NBT; use fixed")
    }
}

private fun emitNbtPutList(b: StringBuilder, ind: String, key: String, acc: String, elem: SerialTy) {
    b.append(ind).append("run {\n")
    b.append(ind).append("    val list = ListTag()\n")
    b.append(ind).append("    for (item in $acc) {\n")
    when (elem) {
        SerialTy.Str -> b.append(ind).append("        list.add(StringTag.valueOf(item))\n")
        SerialTy.I32 -> b.append(ind).append("        list.add(IntTag.valueOf(item))\n")
        SerialTy.I64 -> b.append(ind).append("        list.add(LongTag.valueOf(item))\n")
        SerialTy.Bool -> b.append(ind).append("        list.add(ByteTag.valueOf(item))\n")
        is SerialTy.Nested -> b.append(ind).append("        list.add(item.toNbt())\n")
        is SerialTy.Enum -> if (elem.ordinal) b.append(ind).append("        list.add(ByteTag.valueOf(item.ordinal.toByte()))\n") else b.append(ind).append("        list.add(StringTag.valueOf(item.name))\n")
        else -> {}
    }
    b.append(ind).append("    }\n")
    b.append(ind).append("    tag.put(\"$key\", list)\n")
    b.append(ind).append("}\n")
}

private fun emitFromNbt(b: StringBuilder, cls: SerialClass) {
    b.append("fun ").append(cls.name).append(".Companion.fromNbt(tag: CompoundTag): ").append(cls.name).append(" {\n")
    if (!cls.construct) {
        b.append("    val v = ").append(cls.name).append("()\n")
        b.append("    v.readInto(tag)\n")
        b.append("    return v\n}\n\n")
        return
    }
    for (f in cls.fields) {
        if (f.nullable) {
            b.append("    val ${f.name} = if (tag.contains(\"${f.wireName}\")) ${nbtRead(f.type, f.wireName)} else null\n")
        } else when (val t = f.type) {
            is SerialTy.ListOf -> {
                b.append("    val _${f.name} = tag.getList(\"${f.wireName}\", ${listTagType(t.element)})\n")
                b.append("    val ${f.name} = ArrayList<${typeKt(t.element)}>(_${f.name}.size)\n")
                b.append("    for (i in _${f.name}.indices) ${f.name}.add(${listGet(t.element, "_${f.name}")})\n")
            }
            else -> b.append("    val ${f.name} = ${nbtRead(t, f.wireName)}\n")
        }
    }
    b.append("    return ").append(cls.name).append("(").append(cls.fields.joinToString(", ") { it.name }).append(")\n}\n\n")
}

private fun nbtRead(t: SerialTy, k: String): String = when (t) {
    SerialTy.VarInt, SerialTy.VarLong, SerialTy.VarBigInt -> error("VarLen types use fixed NBT; use @SerialVarLen on Buf only")
    SerialTy.Bool -> "tag.getBoolean(\"$k\")"
    SerialTy.I8 -> "tag.getByte(\"$k\")"
    SerialTy.I16 -> "tag.getShort(\"$k\")"
    SerialTy.I32 -> "tag.getInt(\"$k\")"
    SerialTy.I64 -> "tag.getLong(\"$k\")"
    SerialTy.F32 -> "tag.getFloat(\"$k\")"
    SerialTy.F64 -> "tag.getDouble(\"$k\")"
    SerialTy.Str -> "tag.getString(\"$k\")"
    SerialTy.Bytes -> "tag.getByteArray(\"$k\")"
    SerialTy.Ints -> "tag.getIntArray(\"$k\")"
    SerialTy.Longs -> "tag.getLongArray(\"$k\")"
    SerialTy.BigInt -> "BigInteger(tag.getByteArray(\"$k\"))"
    SerialTy.Uuid -> "tag.getUUID(\"$k\")"
    SerialTy.ResLoc -> "ResourceLocation(tag.getString(\"$k\"))"
    SerialTy.BlockPos -> "BlockPos.of(tag.getLong(\"$k\"))"
    is SerialTy.Enum -> if (t.ordinal) "${t.name}.entries[tag.getByte(\"$k\").toInt() and 0xFF]" else "${t.name}.valueOf(tag.getString(\"$k\"))"
    is SerialTy.Nested -> "${t.name}.fromNbt(tag.getCompound(\"$k\"))"
    is SerialTy.ListOf -> error("list")
}

private fun listTagType(elem: SerialTy): String = when (elem) {
    SerialTy.Str -> "Tag.TAG_STRING.toInt()"
    SerialTy.I32 -> "Tag.TAG_INT.toInt()"
    SerialTy.I64 -> "Tag.TAG_LONG.toInt()"
    SerialTy.I8, SerialTy.Bool -> "Tag.TAG_BYTE.toInt()"
    is SerialTy.Nested -> "Tag.TAG_COMPOUND.toInt()"
    is SerialTy.Enum -> if (elem.ordinal) "Tag.TAG_BYTE.toInt()" else "Tag.TAG_STRING.toInt()"
    else -> "Tag.TAG_COMPOUND.toInt()"
}

private fun listGet(elem: SerialTy, list: String): String = when (elem) {
    SerialTy.Str -> "$list.getString(i)"
    SerialTy.I32 -> "($list.get(i) as net.minecraft.nbt.NumericTag).getAsInt()"
    SerialTy.I64 -> "($list.get(i) as net.minecraft.nbt.NumericTag).getAsLong()"
    SerialTy.Bool -> "($list.get(i) as net.minecraft.nbt.NumericTag).getAsByte().toInt() != 0"
    is SerialTy.Nested -> "${elem.name}.fromNbt($list.getCompound(i))"
    is SerialTy.Enum -> if (elem.ordinal) "${elem.name}.entries[($list.get(i) as net.minecraft.nbt.NumericTag).getAsByte().toInt() and 0xFF]" else "${elem.name}.valueOf($list.getString(i))"
    else -> error("unsupported $elem")
}

private fun typeKt(t: SerialTy): String = when (t) {
    SerialTy.Bool -> "Boolean"
    SerialTy.I8 -> "Byte"
    SerialTy.I16 -> "Short"
    SerialTy.I32 -> "Int"
    SerialTy.I64 -> "Long"
    SerialTy.VarInt -> "Int"
    SerialTy.VarLong -> "Long"
    SerialTy.VarBigInt -> "BigInteger"
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
}

private fun emitWrite(b: StringBuilder, cls: SerialClass) {
    b.append("fun ").append(cls.name).append(".write(buf: FriendlyByteBuf) {\n")
    for (f in cls.fields) {
        val a = acc(f)
        if (f.nullable) {
            b.append("    buf.writeBoolean($a != null)\n")
            b.append("    if ($a != null) {\n")
            emitBufWrite(b, "        ", f.type, "$a!!")
            b.append("    }\n")
        } else emitBufWrite(b, "    ", f.type, a)
    }
    b.append("}\n\n")
}

private fun emitBufWrite(b: StringBuilder, ind: String, t: SerialTy, acc: String) {
    when (t) {
        SerialTy.Bool -> b.append(ind).append("buf.writeBoolean($acc)\n")
        SerialTy.I8 -> b.append(ind).append("buf.writeByte($acc.toInt())\n")
        SerialTy.I16 -> b.append(ind).append("buf.writeShort($acc.toInt())\n")
        SerialTy.I32 -> b.append(ind).append("buf.writeInt($acc)\n")
        SerialTy.I64 -> b.append(ind).append("buf.writeLong($acc)\n")
        SerialTy.VarInt -> b.append(ind).append("buf.writeVarInt($acc)\n")
        SerialTy.VarLong -> b.append(ind).append("buf.writeVarLong($acc)\n")
        SerialTy.F32 -> b.append(ind).append("buf.writeFloat($acc)\n")
        SerialTy.F64 -> b.append(ind).append("buf.writeDouble($acc)\n")
        SerialTy.Str -> b.append(ind).append("buf.writeUtf($acc)\n")
        SerialTy.Bytes -> b.append(ind).append("buf.writeByteArray($acc)\n")
        SerialTy.Ints -> {
            b.append(ind).append("buf.writeVarInt($acc.size)\n")
            b.append(ind).append("for (n in $acc) buf.writeInt(n)\n")
        }
        SerialTy.Longs -> {
            b.append(ind).append("buf.writeVarInt($acc.size)\n")
            b.append(ind).append("for (n in $acc) buf.writeLong(n)\n")
        }
        SerialTy.BigInt, SerialTy.VarBigInt -> b.append(ind).append("buf.writeByteArray($acc.toByteArray())\n")
        SerialTy.Uuid -> b.append(ind).append("buf.writeUUID($acc)\n")
        SerialTy.ResLoc -> b.append(ind).append("buf.writeUtf($acc.toString())\n")
        SerialTy.BlockPos -> b.append(ind).append("buf.writeLong($acc.asLong())\n")
        is SerialTy.Enum -> if (t.ordinal) b.append(ind).append("buf.writeByte($acc.ordinal)\n")
        else b.append(ind).append("buf.writeUtf($acc.name)\n")
        is SerialTy.Nested -> b.append(ind).append("$acc.write(buf)\n")
        is SerialTy.ListOf -> {
            b.append(ind).append("buf.writeVarInt($acc.size)\n")
            b.append(ind).append("for (item in $acc) {\n")
            emitBufWrite(b, ind + "    ", t.element, "item")
            b.append(ind).append("}\n")
        }
    }
}

private fun emitRead(b: StringBuilder, cls: SerialClass) {
    b.append("fun ").append(cls.name).append(".Companion.read(buf: FriendlyByteBuf): ").append(cls.name).append(" {\n")
    if (!cls.construct) {
        b.append("    val v = ").append(cls.name).append("()\n")
        for (f in cls.fields) {
            b.append("    v.${f.name} = ")
            if (f.nullable) b.append("if (buf.readBoolean()) ${bufRead(f.type)} else null\n")
            else b.append("${bufRead(f.type)}\n")
        }
        b.append("    return v\n}\n")
        return
    }
    for (f in cls.fields) {
        b.append("    val ${f.name} = ")
        if (f.nullable) b.append("if (buf.readBoolean()) ${bufRead(f.type)} else null\n")
        else b.append("${bufRead(f.type)}\n")
    }
    b.append("    return ").append(cls.name).append("(").append(cls.fields.joinToString(", ") { it.name }).append(")\n}\n")
}

private fun bufRead(t: SerialTy): String = when (t) {
    SerialTy.Bool -> "buf.readBoolean()"
    SerialTy.I8 -> "buf.readByte()"
    SerialTy.I16 -> "buf.readShort()"
    SerialTy.I32 -> "buf.readInt()"
    SerialTy.I64 -> "buf.readLong()"
    SerialTy.VarInt -> "buf.readVarInt()"
    SerialTy.VarLong -> "buf.readVarLong()"
    SerialTy.F32 -> "buf.readFloat()"
    SerialTy.F64 -> "buf.readDouble()"
    SerialTy.Str -> "buf.readUtf()"
    SerialTy.Bytes -> "buf.readByteArray()"
    SerialTy.Ints -> "IntArray(buf.readVarInt()) { buf.readInt() }"
    SerialTy.Longs -> "LongArray(buf.readVarInt()) { buf.readLong() }"
    SerialTy.BigInt -> "BigInteger(buf.readByteArray())"
    SerialTy.VarBigInt -> "BigInteger(buf.readByteArray())"
    SerialTy.Uuid -> "buf.readUUID()"
    SerialTy.ResLoc -> "ResourceLocation(buf.readUtf())"
    SerialTy.BlockPos -> "BlockPos.of(buf.readLong())"
    is SerialTy.Enum -> if (t.ordinal) "${t.name}.entries[buf.readUnsignedByte().toInt()]" else "${t.name}.valueOf(buf.readUtf())"
    is SerialTy.Nested -> "${t.name}.read(buf)"
    is SerialTy.ListOf -> "run { val n = buf.readVarInt(); val list = ArrayList<${typeKt(t.element)}>(n); repeat(n) { list.add(${bufRead(t.element)}) }; list }"
}

private fun emitReadInto(b: StringBuilder, cls: SerialClass) {
    b.append("fun ").append(cls.name).append(".readInto(tag: CompoundTag) {\n")
    for (f in cls.fields) {
        b.append("    if (tag.contains(\"${f.wireName}\")) {\n")
        when (val t = f.type) {
            is SerialTy.ListOf -> {
                b.append("        val _${f.name} = tag.getList(\"${f.wireName}\", ${listTagType(t.element)})\n")
                b.append("        val ${f.name} = ArrayList<${typeKt(t.element)}>(_${f.name}.size)\n")
                b.append("        for (i in _${f.name}.indices) ${f.name}.add(${listGet(t.element, "_${f.name}")})\n")
                b.append("        this.${f.name} = ${f.name}\n")
            }
            else -> b.append("        this.${f.name} = ${nbtRead(t, f.wireName)}\n")
        }
        b.append("    }\n")
    }
    b.append("}\n\n")
}
