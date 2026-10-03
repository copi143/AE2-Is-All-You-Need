package allyouneed.client.integration.emi.fold

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * [EmiFoldPalette] 内置调色板分配的回归测试（纯逻辑）。
 */
class EmiFoldPaletteTest {
    @Test
    fun `同组键颜色稳定`() {
        assertEquals(
            EmiFoldPalette.colorFor("ae2isallyouneed:*_item_storage_cell"),
            EmiFoldPalette.colorFor("ae2isallyouneed:*_item_storage_cell"),
        )
    }

    @Test
    fun `多组分散到多个槽位`() {
        val seen = (0 until 64)
            .map { EmiFoldPalette.colorFor("ae2isallyouneed:group_$it") }
            .toSet()
        // 64 个不同组键应分散命中全部 5 个槽位（哈希分散，跨进程稳定）
        assertEquals(5, seen.size)
    }

    @Test
    fun `颜色落在内置5色内`() {
        val expected = setOf(
            0xFF5EB6E2.toInt(),
            0xFF26CE9E.toInt(),
            0xFFB7B156.toInt(),
            0xFFEA808A.toInt(),
            0xFFCC83D7.toInt(),
        )
        assertEquals(
            expected,
            (0 until 64).map { EmiFoldPalette.colorFor("k_$it") }.toSet(),
        )
    }
}
