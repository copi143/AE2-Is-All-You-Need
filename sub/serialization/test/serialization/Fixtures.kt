package serialization

import io.github.copi143.serialization.*
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.math.BigInteger
import java.util.UUID

@Serialize(ordinal = true)
enum class OrdKind { A, B, C }

@Serialize
enum class NameKind { X, Y, Z }

@Serialize
data class Inner(val v: Int, val s: String) { companion object }

@Serialize
data class WithMaps(
    val strInt: Map<String, Int>,
    val uuidStr: Map<UUID, String>,
    val intStr: Map<Int, String>,
) { companion object }

@Serialize
data class WithSets(
    val strSet: Set<String>,
    val intSet: Set<Int>,
) { companion object }

@Serialize
data class WithCollections(
    val list: List<Inner>,
    val set: Set<Inner>,
    val mapStrList: Map<String, List<String>>,
    val mapNonStr: Map<Int, Inner>,
) { companion object }

@Serialize
data class WithUnsigned(
    val ub: UByte,
    val us: UShort,
    val ui: UInt,
    val ul: ULong,
) { companion object }

@Serialize
data class WithVarLen(
    @SerialVarLen val vi: Int,
    @SerialVarLen val vl: Long,
    @SerialVarLen val vu: UInt,
    @SerialVarLen val vul: ULong,
) { companion object }

@Serialize
data class WithNullable(
    val id: Int,
    @SerialNullable val note: String?,
    val optMap: Map<String, String>?,
) { companion object }

@Serialize
data class WithPrimitives(
    val b: Boolean,
    val by: Byte,
    val sh: Short,
    val i: Int,
    val l: Long,
    val f: Float,
    val d: Double,
    val s: String,
    val bytes: ByteArray,
    val ints: IntArray,
    val longs: LongArray,
    val big: BigInteger,
    val uuid: UUID,
    val loc: ResourceLocation,
    val pos: BlockPos,
    val ord: OrdKind,
    val named: NameKind,
) {
    companion object {}

    override fun equals(other: Any?): Boolean {
        if (other !is WithPrimitives) return false
        return b == other.b && by == other.by && sh == other.sh && i == other.i && l == other.l &&
            f == other.f && d == other.d && s == other.s &&
            bytes.contentEquals(other.bytes) && ints.contentEquals(other.ints) &&
            longs.contentEquals(other.longs) && big == other.big && uuid == other.uuid &&
            loc == other.loc && pos == other.pos && ord == other.ord && named == other.named
    }
    override fun hashCode() = i
}
