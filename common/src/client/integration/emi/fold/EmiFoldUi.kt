package allyouneed.client.integration.emi.fold

import allyouneed.client.integration.emi.fold.model.FoldKind
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.widget.Bounds
import dev.emi.emi.config.SidebarType
import dev.emi.emi.runtime.EmiDrawContext
import dev.emi.emi.screen.EmiScreenManager
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.function.Supplier

/** 固定两行导航区域；空间从原网格内预留，避免覆盖容器、翻页按钮和排除区域。 */
object EmiFoldUi {
    private const val RESERVED = 36
    private val owners = WeakHashMap<EmiScreenManager.ScreenSpace, WeakReference<EmiScreenManager.SidebarPanel>>()
    private val reserved = WeakHashMap<EmiScreenManager.ScreenSpace, Int>()

    @JvmStatic
    fun createSpace(tx: Int, ty: Int, tw: Int, th: Int, rtl: Boolean, exclusions: List<Bounds>,
                    type: Supplier<SidebarType>, search: Boolean, panel: EmiScreenManager.SidebarPanel): EmiScreenManager.ScreenSpace {
        val top = ty - if (panel.header) 18 else 0
        val fits = tw >= 3 && th >= 3 && exclusions.none { b ->
            b.x() < tx + tw * 18 && b.right() > tx && b.y() < ty + RESERVED && b.bottom() > top
        }
        val offset = if (EmiFoldConfig.enabled && type.get() == SidebarType.INDEX && fits) RESERVED else 0
        return EmiScreenManager.ScreenSpace(tx, ty + offset, tw, th - offset / 18, rtl, exclusions, type, search).also {
            owners[it] = WeakReference(panel)
            if (offset > 0) reserved[it] = offset
        }
    }

    fun panelFor(space: EmiScreenManager.ScreenSpace): EmiScreenManager.SidebarPanel? =
        owners[space]?.get()?.takeIf { it.space === space && it.type == SidebarType.INDEX }
    fun hasToolbar(space: EmiScreenManager.ScreenSpace): Boolean = reserved.containsKey(space)
    @JvmStatic
    fun reservedHeight(panel: EmiScreenManager.SidebarPanel): Int =
        if (panel.type == SidebarType.INDEX) reserved[panel.space] ?: 0 else 0

    @JvmStatic
    fun extendBounds(panel: EmiScreenManager.SidebarPanel, bounds: Bounds): Bounds {
        val space = panel.space ?: return bounds
        val amount = if (panel.type == SidebarType.INDEX) reserved[space] ?: 0 else 0
        return if (amount == 0) bounds else Bounds(bounds.x(), bounds.y() - amount, bounds.width(), bounds.height() + amount)
    }

    private data class Button(val x: Int, val y: Int, val width: Int, val text: Component, val tooltip: Component,
                              val selected: Boolean = false, val enabled: Boolean = true, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx < x + width && my >= y && my < y + 16
    }
    private fun tr(key: String, vararg args: Any) = Component.translatable("tooltip.ae2isallyouneed.emi_fold.$key", *args)

    private fun buttons(panel: EmiScreenManager.SidebarPanel): List<Button> {
        val space = panel.space ?: return emptyList()
        if (!EmiFoldConfig.enabled || panel.type != SidebarType.INDEX || !hasToolbar(space) || !panel.isVisible) return emptyList()
        val view = EmiFoldGroups.view(panel)
        val width = space.tw * 18
        val x = space.tx
        val y = space.ty - RESERVED - if (panel.header) 18 else 0
        val ready = EmiFoldIndex.catalog != null
        val out = ArrayList<Button>()
        val kinds = listOf(null) + FoldKind.entries
        kinds.forEachIndexed { i, kind ->
            val left = x + width * i / kinds.size
            val right = x + width * (i + 1) / kinds.size
            val name = kind?.id ?: "all"
            val count = if (kind == null) view.projection?.kindCounts?.values?.sum() ?: 0 else view.projection?.kindCounts?.get(kind) ?: 0
            out.add(Button(left, y, right - left, tr("kind_${name}_short"), tr("filter", tr("kind_$name"), count),
                view.state.kind == kind, ready) { EmiFoldGroups.selectKind(panel, kind) })
        }
        out.add(Button(x, y + 18, 16, Component.literal("<"), tr("back"), enabled = ready && view.state.canBack) { EmiFoldGroups.back(panel) })
        out.add(Button(x + 16, y + 18, 16, Component.literal("«"), tr("root"), enabled = ready) { EmiFoldGroups.jump(panel, null) })
        val trail = EmiFoldIndex.catalog?.tree?.ancestors(view.state.nodeKey).orEmpty()
        val shown = trail.takeLast(if (width >= 150) 2 else 1)
        val remaining = width - 32
        val fullPath = (listOf(tr("kind_${view.state.kind?.id ?: "all"}").string) + trail.map { it.label }).joinToString(" / ")
        if (shown.isEmpty()) {
            val text = if (!ready) tr("building") else if (view.state.query.isNotEmpty()) tr("search_results", view.projection?.matched ?: 0)
                else tr("kind_${view.state.kind?.id ?: "all"}")
            out.add(Button(x + 32, y + 18, remaining, text, Component.literal(fullPath), enabled = false) {})
        } else shown.forEachIndexed { i, node ->
            val left = x + 32 + remaining * i / shown.size
            val right = x + 32 + remaining * (i + 1) / shown.size
            out.add(Button(left, y + 18, right - left, Component.literal(node.label), Component.literal(fullPath),
                selected = node.key == view.state.nodeKey) { EmiFoldGroups.jump(panel, node.key) })
        }
        return out
    }

    @JvmStatic
    fun draw(panel: EmiScreenManager.SidebarPanel, context: EmiDrawContext, mouseX: Int, mouseY: Int) {
        val controls = buttons(panel)
        if (controls.isEmpty()) return
        val draw = context.raw()
        val font = Minecraft.getInstance().font
        draw.pose().pushPose()
        draw.pose().translate(0f, 0f, 250f)
        var hovered: Button? = null
        for (button in controls) {
            val hover = button.contains(mouseX, mouseY)
            if (hover) hovered = button
            val color = when { button.selected -> 0xFF306C77.toInt(); hover && button.enabled -> 0xFF526578.toInt(); else -> 0xE5222932.toInt() }
            draw.fill(button.x, button.y, button.x + button.width - 1, button.y + 16, color)
            val text = font.plainSubstrByWidth(button.text.string, maxOf(0, button.width - 2))
            draw.drawString(font, text, button.x + (button.width - font.width(text)) / 2, button.y + 4,
                if (button.enabled || button.selected) 0xFFFFFF else 0xAAAAAA, true)
        }
        draw.pose().popPose()
        hovered?.let { draw.renderTooltip(font, it.tooltip, mouseX, mouseY) }
    }

    /** 标题栏点击总是消费，避免向下面的物品或容器透传。 */
    @JvmStatic
    fun click(mouseX: Int, mouseY: Int, button: Int): Boolean {
        for (owner in owners.values.toList()) {
            val panel = owner.get() ?: continue
            val hit = buttons(panel).firstOrNull { it.contains(mouseX, mouseY) } ?: continue
            if (button == 0 && hit.enabled) hit.action()
            return true
        }
        return false
    }
}
