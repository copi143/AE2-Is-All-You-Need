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
    key: AEKey?,
    val wildcard: Ingredient?,
    valLong: Long,
    bigInt: BigInteger?,
) : Object2BigInt<AEKey?>(key, valLong, bigInt) {
    private constructor(mapping: Object2BigInt<AEKey?>, wildcard: Ingredient?) : this(
        mapping.key, wildcard, mapping.valLong, mapping.bigInt
    )

    init {
        require(key == null || wildcard == null) {
            "BigIngredient cannot be both exact and wildcard"
        }
        require(valLong >= 0 || (bigInt != null && bigInt.signum() > 0)) {
            "ingredient amount is negative"
        }
    }

    /** 空槽（无匹配键）。Empty slot (no match key). */
    val isEmptySlot: Boolean get() = key == null && wildcard == null

    /** 通配 [Ingredient]；非通配时为 null。 */
    val ingredient: Ingredient? get() = wildcard

    val isExact: Boolean get() = key != null
    val isWildcard: Boolean get() = wildcard != null

    fun toBigStackOrNull(): BigStack? = if (key == null) null else BigStack(key, valLong, bigInt)

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
            key != null -> {
                val k = AEItemKey.of(stack) ?: return false
                k == key
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

    override operator fun times(scale: Long): BigIngredient =
        BigIngredient((this as Object2BigInt<AEKey?>).times(scale), wildcard)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BigIngredient) return false
        if (key != other.key) return false
        if (wildcard != other.wildcard) return false
        if (valLong != other.valLong) return false
        if (bigInt != other.bigInt) return false
        return true
    }

    override fun hashCode(): Int {
        var h = key.hashCode() xor wildcard.hashCode()
        h = h xor if (valLong < 0) bigInt.hashCode() else valLong.hashCode()
        return h
    }

    override fun toString(): String = when {
        isEmptySlot -> "Empty*$valString"
        key != null -> "$key*$valString"
        else -> "Ingredient*$valString"
    }

    private fun copyAmount(value: Long): BigIngredient = BigIngredient(key, wildcard, value, null)

    private fun copyAmount(value: BigInteger): BigIngredient = if (value.bitLength() < 64) {
        BigIngredient(key, wildcard, value.toLong(), null)
    } else {
        BigIngredient(key, wildcard, -1, value)
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
