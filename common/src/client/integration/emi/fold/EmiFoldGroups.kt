package allyouneed.client.integration.emi.fold

import allyouneed.client.integration.emi.fold.model.FoldBrowserState
import allyouneed.client.integration.emi.fold.model.FoldKind
import allyouneed.client.integration.emi.fold.model.FoldInlineLayout
import allyouneed.client.integration.emi.fold.model.FoldInlineMode
import allyouneed.client.integration.emi.fold.model.FoldTree
import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.config.SidebarType
import dev.emi.emi.screen.EmiScreenManager
import dev.emi.emi.search.EmiSearch
import dev.emi.emi.runtime.EmiReloadManager
import java.util.WeakHashMap

/** EMI 搜索结果 → 固定分类树的一层投影。所有交互状态按侧边栏隔离。 */
object EmiFoldGroups {
    class View {
        val state = FoldBrowserState()
        var projection: FoldTree.Projection? = null
        internal var source: List<EmiIngredient>? = null
        internal var catalog: EmiFoldIndex.Catalog? = null
        internal var revision = -1L
        internal var pageSize = -1
        internal var inlineGroups: Set<String> = emptySet()
        internal var result: List<EmiIngredient> = emptyList()
    }
    private val views = WeakHashMap<EmiScreenManager.SidebarPanel, View>()
    fun view(panel: EmiScreenManager.SidebarPanel): View = views.getOrPut(panel) { View() }
    internal fun classOf(stack: EmiStack): String = runCatching { stack.key.javaClass.name }.getOrDefault("")

    @JvmStatic
    fun fold(space: EmiScreenManager.ScreenSpace, rawSource: List<EmiIngredient>): List<EmiIngredient> {
        val panel = EmiFoldUi.panelFor(space) ?: return rawSource
        if (!EmiFoldConfig.enabled || !EmiFoldUi.hasToolbar(space)) return rawSource
        EmiFoldIndex.ensure()
        val view = view(panel)
        // EMI 搜索在线程中发布结果。不要拿上一轮列表钳制清空搜索时待恢复的页码。
        // 保留上一轮搜索输入，但仍允许按新页容量重排，避免窗口缩小时沿用旧的自动展开列表。
        val pendingSearch = space.search && (EmiSearch.searchThread != null || rawSource !== EmiSearch.stacks)
        val source = if (pendingSearch && view.catalog === EmiFoldIndex.catalog) view.source ?: rawSource else rawSource
        val query = if (!space.search) "" else if (pendingSearch) view.state.query else EmiApi.getSearchText()
        panel.page = view.state.synchronizeQuery(query, panel.page)
        val catalog = EmiFoldIndex.catalog ?: return source
        if (view.source === source && view.catalog === catalog && view.revision == view.state.revision && view.pageSize == space.pageSize) return view.result
        if (view.source !== source && query.isNotEmpty()) panel.page = 0
        val visible = source.mapNotNull { catalog.positions[it] }.toIntArray()
        val base = catalog.tree.project(visible, view.state.kind, view.state.nodeKey)
        val extras = if (view.state.nodeKey == null && view.state.kind == null) source.filter { it !in catalog.positions } else emptyList()
        val projection = base.copy(entries = FoldInlineLayout.arrange(base.entries, space.pageSize, view.state.inlineModes, extras.size))
        val result = projection.entries.map { entry -> when (entry) {
            is FoldTree.Entry.Stack -> entry.inlineGroup?.let { GroupedIngredient(catalog.source[entry.index], it) }
                ?: catalog.source[entry.index]
            is FoldTree.Entry.Group -> FoldedGroupIngredient(entry.key, entry.members.map { catalog.source[it] })
        } }.toMutableList<EmiIngredient>()
        // 第三方索引条目若不在本次快照中，保留其交互能力。
        result.addAll(extras)
        view.projection = projection
        view.inlineGroups = projection.entries.filterIsInstance<FoldTree.Entry.Stack>().mapNotNull { it.inlineGroup }.toSet()
        view.pageSize = space.pageSize
        view.source = source; view.catalog = catalog; view.revision = view.state.revision; view.result = result
        panel.page = FoldBrowserState.clampPage(panel.page, result.size, space.pageSize)
        space.batcher.repopulate()
        return result
    }

    fun enter(panel: EmiScreenManager.SidebarPanel, group: FoldedGroupIngredient): Boolean {
        val catalog = EmiFoldIndex.catalog ?: return false
        if (group.groupKey !in catalog.tree.nodes) return false
        val view = view(panel)
        val displayed = view.projection?.entries?.filterIsInstance<FoldTree.Entry.Group>()?.firstOrNull { it.key == group.groupKey }
            ?: return false
        if (displayed.canInline) view.state.setInline(group.groupKey, FoldInlineMode.EXPANDED)
        else panel.page = view.state.enter(group.groupKey, panel.page)
        refresh(panel)
        return true
    }

    fun collapse(panel: EmiScreenManager.SidebarPanel, member: GroupedIngredient): Boolean {
        val view = view(panel)
        if (member.groupKey !in view.inlineGroups) return false
        view.state.setInline(member.groupKey, FoldInlineMode.COLLAPSED)
        refresh(panel)
        return true
    }

    fun back(panel: EmiScreenManager.SidebarPanel) {
        panel.page = view(panel).state.back(panel.page); refresh(panel)
    }
    fun jump(panel: EmiScreenManager.SidebarPanel, key: String?) {
        panel.page = view(panel).state.jump(key, panel.page); refresh(panel)
    }
    fun selectKind(panel: EmiScreenManager.SidebarPanel, kind: FoldKind?) {
        panel.page = view(panel).state.selectKind(kind); refresh(panel)
    }
    private fun refresh(panel: EmiScreenManager.SidebarPanel) {
        // 仅使显示缓存失效；保留源引用，避免在搜索中把手动展开/返回误判成新搜索结果而跳到第一页。
        view(panel).revision = -1L
        panel.space?.batcher?.repopulate()
    }

    fun title(key: String, fallback: String? = null): String = EmiFoldIndex.catalog?.tree?.nodes?.get(key)?.label
        ?: fallback ?: if (key.startsWith("v2:")) "group" else EmiFoldCluster.displayOf(key)
    fun pathTitle(key: String): String? = EmiFoldIndex.catalog?.tree?.ancestors(key)
        ?.takeIf { it.isNotEmpty() }?.joinToString(" / ") { it.label }
    fun resolve(key: String): List<EmiStack>? = EmiFoldIndex.catalog?.takeIf {
        EmiReloadManager.isLoaded() && it.source === EmiApi.getIndexStacks()
    }?.members(key)
    fun treeReady() {
        views.forEach { (panel, view) ->
            view.state.resetTree(); panel.page = 0
            view.catalog = null; view.result = emptyList(); view.projection = null
            view.inlineGroups = emptySet(); view.source = null; view.pageSize = -1
            refresh(panel)
        }
        EmiScreenManager.repopulatePanels(SidebarType.INDEX)
    }
}
