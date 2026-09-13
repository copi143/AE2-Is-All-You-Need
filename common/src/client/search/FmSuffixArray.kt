package allyouneed.client.search

import allyouneed.indexing.Codec
import allyouneed.indexing.FMIndex

/**
 * 原版 {@code SuffixArray} 的 FM-index 平替引擎（不再继承它，避免 mixin 的
 * delegate 字段在构造器里无限递归）。
 *
 * 由 {@code SuffixArrayFmMixin} 以注入 + cancel 的方式接管原版三个方法的全部行为。
 * 对齐点（逐条对照原版源码）：
 * - 大小写：[add] 原样存入（小写化是 PlainTextSearchTree/ResourceLocationSearchTree
 *   在调用侧做的，本类不碰，和原版一致）；
 * - 分隔：FM-index 的文档分隔符天然阻止跨字符串匹配，对应原版 `-1` 终结符；
 * - 空查询：原版 `compare` 对空模式恒返回 0，即全匹配 → 返回全体 owner；
 * - 排序去重：原版收集 owner 下标 → 去重 → 升序 → 按相等去重（LinkedHashSet），
 *   本类逐字复刻该顺序；
 * - 增量：只支持 add* → generate → search 的一次性流程（原版 refresh 路径同样如此）。
 *
 * 编码选 UTF16：原版按 UTF-16 码元存字符、按码元比较，UTF16 codec 与其逐码元一致
 * （含代理对行为）。构建完成后丢弃原文 [String] 引用，只保留 owner 映射与 FM 索引。
 */
class FmSuffixArray<T> {
    private val owners = ArrayList<T>()
    private val strings = ArrayList<String>()
    private var index: FMIndex? = null

    fun add(obj: T, text: String) {
        owners.add(obj)
        strings.add(text)
    }

    fun generate() {
        index = if (strings.isEmpty()) null else FMIndex.build(strings, Codec.UTF16)
    }

    fun search(query: String): List<T> {
        if (query.isEmpty()) return owners.distinct()
        val idx = index ?: return ArrayList()
        val seen = BooleanArray(owners.size)
        var any = false
        for ((_, document) in idx.search(query)) {
            seen[document] = true
            any = true
        }
        if (!any) return ArrayList()
        val out = ArrayList<T>()
        val added = HashSet<T>()
        for (i in owners.indices) {
            if (!seen[i]) continue
            val o = owners[i]
            if (added.add(o)) out.add(o)
        }
        return out
    }
}
