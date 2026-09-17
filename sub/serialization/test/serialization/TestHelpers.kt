package serialization

import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.math.BigInteger
import java.util.UUID

private fun <T> nbtCheck(v: T, toNbt: (T) -> CompoundTag, fromNbt: (CompoundTag) -> T) {
    val tag = toNbt(v)
    val r = fromNbt(tag)
    check(v == r) { "nbt first round failed: $v vs $r" }
    val tag2 = toNbt(r)
    check(tag == tag2) { "nbt idempotent failed" }
}

private fun <T> bufCheck(v: T, write: (T, FriendlyByteBuf) -> Unit, read: (FriendlyByteBuf) -> T) {
    val buf = FriendlyByteBuf(Unpooled.buffer())
    write(v, buf)
    val r = read(buf)
    check(v == r) { "buf first round failed" }
}

fun checkWithPrimitives(v: WithPrimitives = sampleWithPrimitives()) {
    nbtCheck(v, { it.toNbt() }, { WithPrimitives.fromNbt(it) })
    bufCheck(v, { a, b -> a.write(b) }, { WithPrimitives.read(it) })
    val tag = v.toNbt()
    check(tag.getByte("ord") == 1.toByte() || tag.getByte("ord") == 2.toByte())
}

fun checkWithPrimitives2(v: WithPrimitives = sampleWithPrimitives2()) {
    nbtCheck(v, { it.toNbt() }, { WithPrimitives.fromNbt(it) })
    val tag = v.toNbt()
    check(tag.getByte("ord") == 2.toByte())
    check(tag.getString("named") == "Z")
}

fun checkWithUnsigned(v: WithUnsigned) {
    nbtCheck(v, { it.toNbt() }, { WithUnsigned.fromNbt(it) })
    bufCheck(v, { a, b -> a.write(b) }, { WithUnsigned.read(it) })
}

fun checkWithVarLen(v: WithVarLen) {
    nbtCheck(v, { it.toNbt() }, { WithVarLen.fromNbt(it) })
    bufCheck(v, { a, b -> a.write(b) }, { WithVarLen.read(it) })
}

fun checkWithMaps(v: WithMaps) {
    val tag = v.toNbt()
    val strIntTag = tag.getCompound("strInt")
    check(strIntTag.allKeys.size == v.strInt.size)
    if (v.strInt.isNotEmpty()) check(strIntTag.getInt(v.strInt.keys.first()) == v.strInt.values.first())
    check(tag.getTagType("intStr").toInt() == Tag.TAG_LIST.toInt())
    nbtCheck(v, { it.toNbt() }, { WithMaps.fromNbt(it) })
    bufCheck(v, { a, b -> a.write(b) }, { WithMaps.read(it) })
}

fun checkWithSets(v: WithSets) {
    nbtCheck(v, { it.toNbt() }, { WithSets.fromNbt(it) })
    bufCheck(v, { a, b -> a.write(b) }, { WithSets.read(it) })
}

fun checkWithCollections(v: WithCollections) {
    nbtCheck(v, { it.toNbt() }, { WithCollections.fromNbt(it) })
    bufCheck(v, { a, b -> a.write(b) }, { WithCollections.read(it) })
}

fun checkWithNullable(v: WithNullable) {
    nbtCheck(v, { it.toNbt() }, { WithNullable.fromNbt(it) })
    bufCheck(v, { a, b -> a.write(b) }, { WithNullable.read(it) })
}

fun checkNullableEmptyTag() {
    val emptyTag = CompoundTag()
    emptyTag.putInt("id", 5)
    val decoded = WithNullable.fromNbt(emptyTag)
    check(decoded.id == 5 && decoded.note == null)
}

fun sampleWithPrimitives(): WithPrimitives = WithPrimitives(
    true, 7, 300, -123, 1L shl 40, 1.5f, 2.5, "hi 世界",
    byteArrayOf(1, 2, 3), intArrayOf(1, -1), longArrayOf(9L),
    BigInteger("123456789012345678901234567890"), UUID.randomUUID(),
    ResourceLocation("minecraft:stone"), BlockPos(1, 2, 3), OrdKind.B, NameKind.Y,
)

fun sampleWithPrimitives2(): WithPrimitives = WithPrimitives(
    false, 0, 0, 0, 0, 0f, 0.0, "", byteArrayOf(), intArrayOf(), longArrayOf(),
    BigInteger.ZERO, UUID(0L, 0L), ResourceLocation("minecraft:air"), BlockPos.ZERO, OrdKind.C, NameKind.Z,
)

fun checkMapStringOptimized(map: WithMaps) {
    val tag = map.toNbt()
    val strIntTag = tag.getCompound("strInt")
    check(strIntTag.allKeys.size == map.strInt.size)
}

fun checkEnumStorage(v: WithPrimitives) {
    val tag = v.toNbt()
    check(tag.getByte("ord") == 2.toByte())
    check(tag.getString("named") == "Z")
}
