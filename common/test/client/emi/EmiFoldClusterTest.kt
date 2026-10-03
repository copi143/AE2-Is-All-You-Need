package allyouneed.client.integration.emi.fold

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [EmiFoldCluster] 本地聚类的回归测试（纯逻辑）。
 */
class EmiFoldClusterTest {
    @Test
    fun `同类存储元件按去档位成组`() {
        val a = EmiFoldCluster.keyOf("ae2isallyouneed", "1k_item_storage_cell")
        val b = EmiFoldCluster.keyOf("ae2isallyouneed", "256t_item_storage_cell")
        kotlin.test.assertNotNull(a)
        kotlin.test.assertNotNull(b)
        assertEquals(a, b)
        assertEquals("ae2isallyouneed:*_item_storage_cell", a)
        assertEquals("item storage cell", EmiFoldCluster.displayOf(a!!))
    }

    @Test
    fun `后缀档位同样成组`() {
        val a = EmiFoldCluster.keyOf("ae2isallyouneed", "cell_component_1k")
        val b = EmiFoldCluster.keyOf("ae2isallyouneed", "cell_component_256t")
        kotlin.test.assertNotNull(a)
        assertEquals(a, b)
        assertEquals("ae2isallyouneed:cell_component_*", a)
        assertEquals("cell component", EmiFoldCluster.displayOf(a!!))
    }

    @Test
    fun `creative与普通档位同组`() {
        assertEquals(
            EmiFoldCluster.keyOf("ae2isallyouneed", "1k_crafting_storage"),
            EmiFoldCluster.keyOf("ae2isallyouneed", "creative_crafting_storage"),
        )
    }

    @Test
    fun `自供电与普通能源元件分属两组`() {
        kotlin.test.assertNotEquals(
            EmiFoldCluster.keyOf("ae2isallyouneed", "1k_energy_cell"),
            EmiFoldCluster.keyOf("ae2isallyouneed", "1k_self_powered_energy_cell"),
        )
    }

    @Test
    fun `无档位词不成组`() {
        assertNull(EmiFoldCluster.keyOf("ae2isallyouneed", "item_cell_housing"))
        assertNull(EmiFoldCluster.keyOf("minecraft", "crafting_table"))
    }

    @Test
    fun `纯数字编号不成组`() {
        // 11/13 无数量级单位，不是档位
        assertNull(EmiFoldCluster.keyOf("minecraft", "music_disc_11"))
        kotlin.test.assertNotEquals(
            EmiFoldCluster.keyOf("minecraft", "music_disc_11") ?: "x",
            EmiFoldCluster.keyOf("minecraft", "music_disc_13") ?: "y",
        )
    }

    @Test
    fun `任意命名空间同样聚类`() {
        assertEquals(
            "othermod:foo_*",
            EmiFoldCluster.keyOf("othermod", "foo_64k"),
        )
    }

    @Test
    fun `尾词跨命名空间成键`() {
        assertEquals("~planks", EmiFoldCluster.suffixOf("oak_planks"))
        assertEquals(
            EmiFoldCluster.suffixOf("oak_planks"),
            EmiFoldCluster.suffixOf("spruce_planks"),
        )
        assertEquals("~sword", EmiFoldCluster.suffixOf("diamond_sword"))
        // 宝船与普通船同尾词
        assertEquals(
            EmiFoldCluster.suffixOf("oak_boat"),
            EmiFoldCluster.suffixOf("oak_chest_boat"),
        )
    }

    @Test
    fun `档位词作尾词不成键`() {
        assertNull(EmiFoldCluster.suffixOf("foo_creative"))
        assertNull(EmiFoldCluster.suffixOf("cell_component_1k"))
        assertNull(EmiFoldCluster.suffixOf(""))
    }

    @Test
    fun `keysOf档位优先在前`() {
        assertEquals(
            listOf("ae2isallyouneed:*_item_storage_cell", "~cell"),
            EmiFoldCluster.keysOf("ae2isallyouneed", "1k_item_storage_cell"),
        )
        assertEquals(
            listOf("~planks"),
            EmiFoldCluster.keysOf("minecraft", "oak_planks"),
        )
        assertEquals(
            listOf("music_disc~", "~11"),
            EmiFoldCluster.keysOf("minecraft", "music_disc_11"),
        )
    }

    @Test
    fun `唱片按前缀家族成键`() {
        assertEquals("music_disc~", EmiFoldCluster.prefixOf("music_disc_11"))
        assertEquals(
            EmiFoldCluster.prefixOf("music_disc_11"),
            EmiFoldCluster.prefixOf("music_disc_13"),
        )
        assertEquals(
            EmiFoldCluster.prefixOf("music_disc_11"),
            EmiFoldCluster.prefixOf("music_disc_cat"),
        )
    }

    @Test
    fun `单词词干不成键`() {
        // oak_planks 去尾剩 oak，太泛
        assertNull(EmiFoldCluster.prefixOf("oak_planks"))
        assertNull(EmiFoldCluster.prefixOf("torch"))
        assertNull(EmiFoldCluster.prefixOf(""))
    }

    @Test
    fun `词干含档位词不成键`() {
        // 1k_item_storage_cell 去尾剩 [1k, item, storage]，含档位词 → 走档位族通道
        assertNull(EmiFoldCluster.prefixOf("1k_item_storage_cell"))
        // cell_component_1k 去尾剩 [cell, component]，干净 → 有前缀键，
        // 但 fold 层档位优先认领，不冲突
        assertEquals("cell_component~", EmiFoldCluster.prefixOf("cell_component_1k"))
    }

    @Test
    fun `展示名覆盖四种键`() {
        assertEquals("boat", EmiFoldCluster.displayOf("minecraft:~boat"))
        assertEquals("music disc", EmiFoldCluster.displayOf("music_disc~"))
        assertEquals("item storage cell", EmiFoldCluster.displayOf("ae2isallyouneed:*_item_storage_cell"))
        // 复合键去类后缀后展示一致
        assertEquals(
            "boat",
            EmiFoldCluster.displayOf("minecraft:~boat\u0001net.minecraft.world.item.BoatItem"),
        )
        assertEquals(
            "item storage cell",
            EmiFoldCluster.displayOf("ae2isallyouneed:*_item_storage_cell\u0001allyouneed.CellItem"),
        )
    }

    @Test
    fun `复合键同词不同类即不同组`() {
        val a = EmiFoldCluster.qualify("~rod", "net.minecraft.world.item.Item")
        val b = EmiFoldCluster.qualify("~rod", "net.minecraft.world.item.FishingRodItem")
        kotlin.test.assertNotEquals(a, b)
        assertEquals("~rod", EmiFoldCluster.baseKey(a))
        assertEquals(
            listOf(a),
            EmiFoldCluster.compositeKeys("minecraft", "blaze_rod", "net.minecraft.world.item.Item")
                .filter { it == a },
        )
    }

    @Test
    fun `阈值判定`() {
        kotlin.test.assertTrue(EmiFoldCluster.tierQualifies(4))
        kotlin.test.assertFalse(EmiFoldCluster.tierQualifies(3))
        kotlin.test.assertTrue(EmiFoldCluster.suffixQualifies(4))
        kotlin.test.assertTrue(EmiFoldCluster.suffixQualifies(64))
        kotlin.test.assertFalse(EmiFoldCluster.suffixQualifies(3))
        kotlin.test.assertFalse(EmiFoldCluster.suffixQualifies(65))
    }
}
