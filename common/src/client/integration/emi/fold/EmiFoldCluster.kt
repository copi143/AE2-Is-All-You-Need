package allyouneed.client.integration.emi.fold

/**
 * 本地聚类：无外部配置，仅按物品 id 的分词规律推断同类组。
 *
 * 三层键（按优先级）：
 * 1. 档位族键 `namespace:词_序列`（变体位记 `*`）：id 含档位词（带数量级单位的
 *    数字如 `1k`、`256t`，或 `creative`/`unlimited`/`max`），除变体位外词序列
 *    完全相同的物品即一类，如 `1k_item_storage_cell` → `ae2isallyouneed:*_item_storage_cell`。
 *    无变体词的物品不可能成组（id 唯一）；`music_disc_11`/`music_disc_13` 这类
 *    纯数字编号（无单位）也不走本层。
 * 2. 前缀家族键 `词干~`（跨命名空间）：去尾词后剩余词干（≥2 词、不含档位词）
 *    相同的物品即一类，如 15 张 `music_disc_*` → `music_disc~`。
 *    单词词干（如 `oak`）太泛，直接不成键。
 * 3. 全局尾词键 `~尾词`（跨命名空间）：如 `oak_planks`/`spruce_planks` →
 *    `~planks`，`iron_sword`/`diamond_sword` → `~sword`。尾词本身是档位词时
 *    不成键（如 `*_creative`），单成员占比极高的泛词靠数量上限挡掉。
 *
 * 同词还不够：最终组键是词法键 + 物品类名的复合键（分隔符 `\u0001`），
 * 同词不同类（如 `blaze_rod`[Item] / `fishing_rod`[FishingRodItem] /
 * `lightning_rod`[BlockItem]）会被拆开，各自计数，达标才折。
 * 类取自 [EmiStack.getKey] 的运行时类，精确匹配（不考虑继承），无注册表访问、
 * 离线可用、跨进程稳定。
 *
 * tags 刻意不用：1.20.1 原版缺少武器/护甲类物品标签，公共标签覆盖随加载器/
 * 整合包变化，且需同步后的注册表访问；类关系已覆盖主要误并场景，收益明确、
 * 成本几乎为零。
 *
 * 纯 Kotlin，无 Minecraft / EMI / Gson / 文件 IO 依赖，可单元测试。
 * 匹配结果按输入记忆化（纯函数永不过期），每个物品 id 只算一遍。
 */
object EmiFoldCluster {
    /** 档位族成组的最小可见成员数。 */
    const val MIN_SIZE = 4

    /** 尾词/前缀家族成组的成员数区间（上限防 `~block` 这类巨型组）。 */
    const val SUFFIX_MIN = 4
    const val SUFFIX_MAX = 64

    /** 档位数字：整数 + 数量级单位（对应 formatScaledUnit 的 b/k/m/g/t/p/e/z/y/r/q）。 */
    private val TIER_TOKEN = Regex("^\\d+[bkmgtpezyrq]$")

    /** 非数字的档位词。 */
    private val WORD_VARIANTS = setOf("creative", "unlimited", "max")

    private fun isVariant(token: String): Boolean =
        TIER_TOKEN.matches(token) || token in WORD_VARIANTS

    private fun tokensOf(path: String): List<String> =
        path.lowercase().split('_', '/').filter { it.isNotEmpty() }

    private val keyMemo: MutableMap<String, String?> = HashMap()
    private val suffixMemo: MutableMap<String, String?> = HashMap()

    /**
     * 档位族键，无变体词时返回 null（必然不成组）。
     */
    fun keyOf(namespace: String, path: String): String? {
        val memoKey = "$namespace\u0000$path"
        synchronized(keyMemo) {
            if (keyMemo.containsKey(memoKey)) return keyMemo[memoKey]
        }
        val tokens = tokensOf(path)
        val result = if (tokens.isEmpty() || tokens.none { isVariant(it) }) {
            null
        } else {
            "$namespace:" + tokens.joinToString("_") { if (isVariant(it)) "*" else it }
        }
        synchronized(keyMemo) { keyMemo[memoKey] = result }
        return result
    }

    /**
     * 全局尾词键（跨命名空间），尾词为档位词或分词为空时返回 null。
     */
    fun suffixOf(path: String): String? {
        synchronized(suffixMemo) {
            if (suffixMemo.containsKey(path)) return suffixMemo[path]
        }
        val tokens = tokensOf(path)
        val last = tokens.lastOrNull()
        val result = if (last == null || isVariant(last)) null else "~$last"
        synchronized(suffixMemo) { suffixMemo[path] = result }
        return result
    }

    private val prefixMemo: MutableMap<String, String?> = HashMap()

    /**
     * 前缀家族键（跨命名空间）：去尾词后词干 ≥2 词且不含档位词时成键，
     * 如 `music_disc_11` → `music_disc~`。单词词干太泛不成键。
     */
    fun prefixOf(path: String): String? {
        synchronized(prefixMemo) {
            if (prefixMemo.containsKey(path)) return prefixMemo[path]
        }
        val tokens = tokensOf(path)
        val stem = if (tokens.size >= 3) tokens.dropLast(1) else emptyList()
        val result = if (stem.size < 2 || stem.any { isVariant(it) }) {
            null
        } else {
            stem.joinToString("_") + "~"
        }
        synchronized(prefixMemo) { prefixMemo[path] = result }
        return result
    }

    /** 该物品参与的全部组键（档位 > 前缀 > 尾词）。 */
    fun keysOf(namespace: String, path: String): List<String> =
        listOfNotNull(keyOf(namespace, path), prefixOf(path), suffixOf(path))

    /**
     * 类名分隔符：不可能是 id/类名合法字符，组键解析时据此剥离类后缀。
     * `*`（变体位）、`~`（词族标记）同理不会与 id 字符集冲突。
     */
    const val CLASS_SEP = "\u0001"

    /** 词法键 + 物品类名的复合键，同词不同类即不同组。 */
    fun qualify(lexicalKey: String, className: String): String =
        if (className.isEmpty()) lexicalKey else lexicalKey + CLASS_SEP + className

    /** 复合键的词法部分（去类后缀），展示/比对旧键用。 */
    fun baseKey(groupKey: String): String = groupKey.substringBefore(CLASS_SEP)

    /** 该物品参与的全部复合组键（档位 > 前缀 > 尾词）。 */
    fun compositeKeys(namespace: String, path: String, className: String): List<String> =
        keysOf(namespace, path).map { qualify(it, className) }

    /** 组键转展示名：先去类后缀；档位族去变体位，前缀/尾词组直接取词。 */
    fun displayOf(groupKey: String): String {
        val inner = baseKey(groupKey).substringAfter(':')
        if (inner.startsWith("~")) return inner.removePrefix("~").replace('_', ' ')
        if (inner.endsWith("~")) return inner.dropLast(1).replace('_', ' ')
        return inner.split('_').filter { it != "*" && it.isNotEmpty() }.joinToString(" ")
    }

    /** 档位族达到折叠门槛。 */
    fun tierQualifies(count: Int): Boolean = count >= MIN_SIZE

    /** 词族（尾词/前缀）达到折叠门槛（含上限）。 */
    fun suffixQualifies(count: Int): Boolean = count in SUFFIX_MIN..SUFFIX_MAX
}
