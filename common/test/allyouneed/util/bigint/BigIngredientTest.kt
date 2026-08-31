package allyouneed.util.bigint

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * [BigIngredient.hashCode] 的回归测试。
 *
 * 已覆盖 bug：hashCode 直接对可空 [allyouneed.util.bigint.BigIngredient.key] /
 * [allyouneed.util.bigint.BigIngredient.wildcard] 调用 `hashCode()`，任何形态（exact/wildcard/empty）
 * 都会触发空指针（init 保证两者不同时非空）。
 */
class BigIngredientTest {

    @Test
    fun `empty slot hashCode is null safe and stable`() {
        val empty = BigIngredient.ofEmpty()
        val first = empty.hashCode()
        val second = BigIngredient.ofEmpty().hashCode()
        assertEquals(first, second)
    }

    @Test
    fun `empty slot can be stored in hash containers`() {
        val set = HashSet<BigIngredient>()
        set.add(BigIngredient.ofEmpty())
        set.add(BigIngredient.ofEmpty())
        val map = HashMap<BigIngredient, Int>()
        map[BigIngredient.ofEmpty()] = 1

        assertEquals(1, set.size)
        assertEquals(1, map.size)
        assertEquals(1, map[BigIngredient.ofEmpty()])
    }
}