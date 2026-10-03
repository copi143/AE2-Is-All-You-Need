package allyouneed.client.integration.emi.fold.model

/** 每个侧边栏独立保存普通浏览与搜索导航。EMI 自己负责搜索词语法及匹配。 */
class FoldBrowserState {
    private data class Frame(val key: String?, var page: Int,
                             val inline: MutableMap<String, FoldInlineMode> = hashMapOf())
    private val browse = arrayListOf(Frame(null, 0))
    private var search = arrayListOf(Frame(null, 0))
    var query: String = ""
        private set
    var kind: FoldKind? = null
        private set
    var revision: Long = 0
        private set
    private val route get() = if (query.isEmpty()) browse else search
    val nodeKey: String? get() = route.last().key
    val canBack: Boolean get() = route.size > 1
    val inlineModes: Map<String, FoldInlineMode> get() = route.last().inline

    fun setInline(key: String, mode: FoldInlineMode) {
        if (route.last().inline.put(key, mode) != mode) revision++
    }

    fun synchronizeQuery(value: String, page: Int): Int {
        route.last().page = page
        if (query == value) return page
        query = value
        if (value.isNotEmpty()) search = arrayListOf(Frame(null, 0))
        revision++
        return route.last().page
    }

    fun enter(key: String, page: Int): Int {
        route.last().page = page
        route.add(Frame(key, 0)); revision++
        return 0
    }

    fun back(page: Int): Int {
        route.last().page = page
        if (canBack) { route.removeAt(route.lastIndex); revision++ }
        return route.last().page
    }

    fun jump(key: String?, page: Int): Int {
        route.last().page = page
        val index = route.indexOfLast { it.key == key }
        if (index >= 0) while (route.lastIndex > index) route.removeAt(route.lastIndex)
        else {
            while (route.size > 1) route.removeAt(route.lastIndex)
            if (key != null) route.add(Frame(key, 0))
        }
        revision++
        return route.last().page
    }

    fun selectKind(value: FoldKind?): Int {
        if (kind == value) return route.last().page
        kind = value
        browse.clear(); browse.add(Frame(null, 0))
        search.clear(); search.add(Frame(null, 0))
        revision++
        return 0
    }

    fun resetTree() {
        browse.clear(); browse.add(Frame(null, 0))
        search.clear(); search.add(Frame(null, 0))
        revision++
    }

    companion object {
        fun clampPage(page: Int, entries: Int, pageSize: Int): Int =
            page.coerceIn(0, if (pageSize <= 0 || entries == 0) 0 else (entries - 1) / pageSize)
    }
}
