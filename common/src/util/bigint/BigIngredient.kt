package allyouneed.util.bigint

import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import java.math.BigInteger

/**
 * 用 [Long] / [BigInteger] 表示数量的配方输入规格，数量语义与 [BigStack] 一致：
 * - 数量不为负；在 [Long] 范围内用 [valLong]，超出时 [valLong]=-1 且 [valBig] 存真值。
 * - 匹配键可为精确 [AEKey]、原版 [Ingredient] 通配（item/tag），或空槽。
 *
 * Recipe input with BigStack-like amount storage.
 * Match key: exact [AEKey], wildcard [Ingredient], or empty slot.
 */
class BigIngredient private constructor(
    private val exactKey: AEKey?,
    private val wildcard: Ingredient?,
    val valLong: Long,
    private val bigInt: BigInteger?,
) {
    init {
        assert(exactKey == null || wildcard == null) {
            "BigIngredient cannot be both exact and wildcard"
        }
        assert(valLong >= 0 || (bigInt != null && bigInt > BigInteger.ZERO)) {
            "ingredient amount is negative"
        }
    }

    val valIntSaturate: Int
        get() = if (valLong < 0 || valLong > Int.MAX_VALUE.toLong()) Int.MAX_VALUE else valLong.toInt()
    val valLongSaturate: Long get() = if (valLong < 0) Long.MAX_VALUE else valLong
    val valBig: BigInteger get() = bigInt ?: BigInteger.valueOf(valLong)
    val valString: String get() = bigInt?.toString() ?: valLong.toString()
    val isZero: Boolean get() = valLong == 0L

    /** 空槽（无匹配键）。Empty slot (no match key). */
    val isEmptySlot: Boolean get() = exactKey == null && wildcard == null

    /** 精确 [AEKey]；非精确时为 null。 */
    val key: AEKey? get() = exactKey

    /** 通配 [Ingredient]；非通配时为 null。 */
    val ingredient: Ingredient? get() = wildcard

    val isExact: Boolean get() = exactKey != null
    val isWildcard: Boolean get() = wildcard != null

    /**
     * 容器槽物品是否满足本规格（种类 + 数量下限）。
     * Whether the stack satisfies this ingredient (kind + min amount).
     */
    fun test(stack: ItemStack): Boolean {
        if (isEmptySlot || isZero) {
            return stack.isEmpty
        }
        if (stack.isEmpty) return false
        val kindOk = when {
            exactKey != null -> {
                val k = AEItemKey.of(stack) ?: return false
                k == exactKey
            }
            wildcard != null -> !wildcard.isEmpty && wildcard.test(stack)
            else -> false
        }
        return kindOk && BigInteger.valueOf(stack.count.toLong()) >= valBig
    }

    fun setSize(value: Long): BigIngredient {
        require(value >= 0) { "Amount cannot be negative" }
        if (isEmptySlot) return ofEmpty()
        return copyAmount(value)
    }

    fun setSize(value: BigInteger): BigIngredient {
        require(value.signum() >= 0) { "Amount cannot be negative" }
        if (isEmptySlot) return ofEmpty()
        return copyAmount(value)
    }

    operator fun times(scale: Long): BigIngredient {
        require(scale >= 0) { "Cannot multiply by a negative number." }
        return if (scale == 0L) {
            setSize(0L)
        } else if (bigInt != null) {
            copyAmount(valBig * BigInteger.valueOf(scale))
        } else {
            val v = runCatching { Math.multiplyExact(valLong, scale) }.getOrNull()
            if (v != null) {
                copyAmount(v)
            } else {
                copyAmount(BigInteger.valueOf(valLong) * BigInteger.valueOf(scale))
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigIngredient) return false
        if (exactKey != other.exactKey) return false
        if (wildcard != other.wildcard) return false
        if (valLong != other.valLong) return false
        if (bigInt != other.bigInt) return false
        return true
    }

    override fun hashCode(): Int {
        var h = exactKey.hashCode() xor wildcard.hashCode()
        h = h xor if (valLong < 0) bigInt.hashCode() else valLong.hashCode()
        return h
    }

    override fun toString(): String = when {
        isEmptySlot -> "Empty*$valString"
        exactKey != null -> "$exactKey*$valString"
        else -> "Ingredient*$valString"
    }

    private fun copyAmount(value: Long): BigIngredient =
        BigIngredient(exactKey, wildcard, value, null)

    private fun copyAmount(value: BigInteger): BigIngredient =
        if (value.bitLength() < 64) {
            BigIngredient(exactKey, wildcard, value.toLong(), null)
        } else {
            BigIngredient(exactKey, wildcard, -1, value)
        }

    companion object {
        private val EMPTY = BigIngredient(null, null, 0L, null)

        @JvmStatic
        fun ofEmpty(): BigIngredient = EMPTY

        @JvmStatic
        fun from(key: AEKey, value: Long): BigIngredient {
            require(value >= 0) { "amount negative" }
            return if (value == 0L) ofEmpty() else BigIngredient(key, null, value, null)
        }

        @JvmStatic
        fun from(key: AEKey, value: BigInteger): BigIngredient {
            require(value.signum() >= 0) { "amount negative" }
            if (value.signum() == 0) return ofEmpty()
            return if (value.bitLength() < 64) {
                BigIngredient(key, null, value.toLong(), null)
            } else {
                BigIngredient(key, null, -1, value)
            }
        }

        @JvmStatic
        fun from(stack: BigStack): BigIngredient = from(stack.key, stack.valBig)

        @JvmStatic
        fun from(ingredient: Ingredient, value: Long): BigIngredient {
            require(value >= 0) { "amount negative" }
            if (value == 0L || ingredient.isEmpty) return ofEmpty()
            return BigIngredient(null, ingredient, value, null)
        }

        @JvmStatic
        fun from(ingredient: Ingredient, value: BigInteger): BigIngredient {
            require(value.signum() >= 0) { "amount negative" }
            if (value.signum() == 0 || ingredient.isEmpty) return ofEmpty()
            return if (value.bitLength() < 64) {
                BigIngredient(null, ingredient, value.toLong(), null)
            } else {
                BigIngredient(null, ingredient, -1, value)
            }
        }
    }
}
