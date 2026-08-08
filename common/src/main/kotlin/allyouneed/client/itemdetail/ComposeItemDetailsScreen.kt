package allyouneed.client.itemdetail

import allyouneed.client.compose.material.ItemSlot
import allyouneed.client.compose.platform.ComposeScreen
import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.itemdetail.styling.DetailsStyling.COLOR_VALUE
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Compose 渲染的 item-details 屏幕,用作无 EMI/JEI 时的默认渲染器。
 *
 * 面板、标题、内容行与滚动条全部走官方 Compose 布局 + [McGraphics] 文字旁路;只有 header 的
 * 物品堆叠预览需要 MC 方块渲染管线,留在 [render] 里用 [GuiGraphics.renderItem] 旁路绘制。
 *
 * 所有几何都以"逻辑画布坐标"表达:ComposeOwner 把根约束设为屏幕/缩放,再用 pose.scale 画到屏幕,
 * 因此面板用 `(逻辑尺寸 - 面板尺寸) / 2` 居中即可在任何缩放系数下保持屏幕居中,滚动判定也必须
 * 用除以缩放后的鼠标坐标才与面板实际屏幕位置一致。
 *
 * 内容区没有常规 clip(McCanvas 的 clipRect 是 no-op),因此采用"虚拟化可见行 + 硬件 scissor"策略:
 * 按固定行高预计算每一行的纵向位置,只组合落在内容框附近的行;完整可见/完全越界的行直接跳过,
 * 只有与内容框部分重叠的行用 [GuiGraphics.enableScissor] 做像素级裁切,避免越界行残影。
 * 裁切矩形不再手工推导面板几何,而是在 drawBehind 里读取当前 modelview pose(即 drawString
 * 用来变换字形的同一个矩阵),直接得到节点原点与缩放,保证 scissor 区域与文本绘制严格对齐。
 * 滚动分为两路:滚轮只更新目标值 [scrollTarget],动画在 [render] 里**每帧同步推进**(先更新
 * [scrollDisplay] 再走 super.render → 快照应用 → 布局 → 绘制,同一帧内完成,刷新率=游戏帧率,
 * 无协程/重组时序滞后),显示值按时间常数向目标指数收敛——中途改目标不会重启,快速滚轮平滑不顿挫;
 * 滚动条拖拽则直接写显示值并同步目标,即时 1:1 跟手不经过动画。滚动条 track 点击为跳转(直接定位)。
 */
class ComposeItemDetailsScreen(
    private val details: ItemDetails,
) : ComposeScreen(Component.literal("Block Details")) {

    /** Target scroll position, updated by wheel events and scrollbar clicks. */
    private var scrollTarget by mutableFloatStateOf(0f)

    /** Animated display scroll position: converges to [scrollTarget] every game frame in [render]. */
    private var scrollDisplay by mutableFloatStateOf(0f)

    /** Nanos of the previous render, for computing the frame delta of the smoothing step. */
    private var lastFrameNanos = -1L

    private val rows: List<Row> by lazy { buildRows() }
    private val maxScroll: Int by lazy {
        val lastY = rows.lastOrNull()?.y ?: 0
        max(0, lastY + ItemDetailsLayout.LINE_HEIGHT - ItemDetailsLayout.CONTENT_HEIGHT)
    }

    private data class Row(val text: Component, val y: Int, val x: Int)

    private fun buildRows(): List<Row> {
        val out = ArrayList<Row>()
        var y = 0
        for (section in details.sections) {
            out += Row(section.title, y, 4)
            y += ItemDetailsLayout.LINE_HEIGHT
            for (entry in section.lines) {
                out += Row(entry, y, 8)
                y += ItemDetailsLayout.LINE_HEIGHT
            }
            y += ItemDetailsLayout.SECTION_GAP
        }
        return out
    }

    @Composable
    override fun Content() {
        val max = maxScroll

        // The panel is centred via layout (Alignment.Center) against the root constraints, which the
        // owner refreshes every frame from the current window size. Nothing here reads the screen
        // size or zoom factor during composition, so window resize re-centres immediately and zoom
        // never causes a one-frame misalignment (recomposition lags a frame behind the render pass).
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ItemDetailsLayout.WIDTH.dp, ItemDetailsLayout.HEIGHT.dp),
            ) {
                // 面板
                Box(
                    Modifier
                        .size(ItemDetailsLayout.WIDTH.dp, ItemDetailsLayout.HEIGHT.dp)
                        .background(Color(0xC0101010)),
                )
                Box(
                    Modifier
                        .size(ItemDetailsLayout.WIDTH.dp, ItemDetailsLayout.HEIGHT.dp)
                        .drawBehind { drawRect(color = Color.White, style = Stroke(1f)) },
                )

                // Header:物品 slot(不可交互,EMI/vanilla 渲染),标题,关闭按钮
                ItemSlot(
                    stack = details.stack,
                    modifier = Modifier.offset(ItemDetailsLayout.PADDING.dp, ItemDetailsLayout.PADDING.dp),
                )
                McText(
                    text = details.title,
                    modifier = Modifier.offset(ItemDetailsLayout.TITLE_X.dp, (ItemDetailsLayout.PADDING + 4).dp),
                    maxWidth = ItemDetailsLayout.CONTENT_WIDTH - ItemDetailsLayout.TITLE_X,
                )
                Box(
                    modifier = Modifier
                        .offset((ItemDetailsLayout.WIDTH - ItemDetailsLayout.PADDING - 14).dp, ItemDetailsLayout.PADDING.dp)
                        .size(14.dp)
                        .background(Color(0xAA404040))
                        .drawBehind { drawRect(color = Color.White, style = Stroke(1f)) }
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    McText(Component.literal("✕"), maxWidth = 12)
                }

                // 内容框
                Box(
                    modifier = Modifier
                        .offset(ItemDetailsLayout.PADDING.dp, ItemDetailsLayout.CONTENT_TOP.dp)
                        .size(ItemDetailsLayout.CONTENT_WIDTH.dp, ItemDetailsLayout.CONTENT_HEIGHT.dp),
                ) {
                    Box(
                        Modifier
                            .size(ItemDetailsLayout.CONTENT_WIDTH.dp, ItemDetailsLayout.CONTENT_HEIGHT.dp)
                            .background(Color(0x66000000)),
                    )
                    Box(
                        Modifier
                            .size(ItemDetailsLayout.CONTENT_WIDTH.dp, ItemDetailsLayout.CONTENT_HEIGHT.dp)
                            .drawBehind { drawRect(color = Color(0xFF333333), style = Stroke(1f)) },
                    )

                    for (row in rows) {
                        val y = row.y - scrollDisplay
                        if (y >= -ItemDetailsLayout.LINE_HEIGHT.toFloat() && y < ItemDetailsLayout.CONTENT_HEIGHT.toFloat()) {
                            McText(
                                text = row.text,
                                modifier = Modifier.offset(row.x.dp, y.dp),
                                color = COLOR_VALUE,
                                maxWidth = ItemDetailsLayout.CONTENT_WIDTH - row.x,
                                clipFrame = Rect(
                                    -row.x.toFloat(),
                                    -y,
                                    (ItemDetailsLayout.CONTENT_WIDTH - row.x).toFloat(),
                                    (ItemDetailsLayout.CONTENT_HEIGHT - y).toFloat(),
                                ),
                            )
                        }
                    }

                    if (max > 0) {
                        val trackHeight = ItemDetailsLayout.CONTENT_HEIGHT
                        val barHeight = max(16, trackHeight * trackHeight / (trackHeight + max))
                        val travel = trackHeight - barHeight
                        val barY = if (max == 0) 0 else (travel * scrollDisplay / max).toInt()
                        Box(
                            Modifier
                                .offset((ItemDetailsLayout.CONTENT_WIDTH - 4).dp, 0.dp)
                                .size(4.dp, trackHeight.dp)
                                .background(Color(0xAA444444))
                                // Clicking the track jumps directly; dragging scrubs 1:1 with the
                                // cursor (writes the display value immediately, skipping the smooth
                                // animation). Positions are logical (root constraints are /scale), so
                                // the mapping to the scroll value stays consistent with the wheel.
                                .pointerInput(max, travel) {
                                    var dragging = false
                                    var grabOffset = 0f
                                    fun seek(y: Float) {
                                        val frac = ((y - grabOffset) / travel).coerceIn(0f, 1f)
                                        val value = frac * max
                                        scrollDisplay = value
                                        scrollTarget = value
                                    }
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val p = event.changes.firstOrNull()?.position ?: continue
                                            when (event.type) {
                                                PointerEventType.Press -> {
                                                    dragging = true
                                                    val barCenter = travel * scrollDisplay / max
                                                    grabOffset = p.y - barCenter
                                                    seek(p.y)
                                                }
                                                PointerEventType.Move -> if (dragging) seek(p.y)
                                                PointerEventType.Release -> dragging = false
                                                else -> Unit
                                            }
                                        }
                                    }
                                },
                        )
                        Box(
                            Modifier
                                .offset((ItemDetailsLayout.CONTENT_WIDTH - 3).dp, barY.dp)
                                .size(2.dp, barHeight.dp)
                                .background(Color(0xFFAAAAAA)),
                        )
                    }
            }
        }
        }
    }

    @Composable
    private fun McText(
        text: Component,
        modifier: Modifier = Modifier,
        color: Int = COLOR_VALUE,
        maxWidth: Int = Int.MAX_VALUE,
        clipFrame: Rect? = null,
    ) {
        val font = Minecraft.getInstance().font
        Layout(
            content = {},
            modifier = modifier.drawBehind {
                val g = McGraphics.current ?: return@drawBehind
                drawClipped(g, font, text, min(font.width(text), maxWidth), color, clipFrame)
            },
        ) { _, constraints: androidx.compose.ui.unit.Constraints ->
            val w = constraints.constrainWidth(min(font.width(text), maxWidth))
            val h = constraints.constrainHeight(font.lineHeight)
            layout(w, h) {}
        }
    }

    /**
     * Draws [text] at the node's local origin, truncated to [widthPx]. When [clipFrame] (a rect in
     * node-local coordinates describing the content frame) is provided and the glyph box only
     * partially overlaps it, a hardware scissor clips the text to the frame; rows fully inside or
     * fully outside the frame skip the scissor entirely.
     *
     * The clip rectangle is derived from the live modelview pose instead of manually re-deriving
     * the panel geometry: [GuiGraphics.drawString] transforms glyphs with that same
     * `pose().last().pose()` matrix, so the scissor region is always pixel-aligned with the drawn
     * text, regardless of zoom or window size.
     */
    private fun drawClipped(
        g: GuiGraphics,
        font: Font,
        text: Component,
        widthPx: Int,
        color: Int,
        clipFrame: Rect?,
    ) {
        if (clipFrame == null) {
            drawText(g, font, text, widthPx, color)
            return
        }
        if (clipFrame.left <= 0f && clipFrame.top <= 0f &&
            clipFrame.right >= widthPx.toFloat() && clipFrame.bottom >= font.lineHeight.toFloat()
        ) {
            drawText(g, font, text, widthPx, color)
            return
        }
        if (clipFrame.right <= 0f || clipFrame.bottom <= 0f ||
            clipFrame.left >= widthPx.toFloat() || clipFrame.top >= font.lineHeight.toFloat()
        ) {
            return
        }
        val matrix = g.pose().last().pose()
        val nodeX = matrix.m30()
        val nodeY = matrix.m31()
        val scaleX = matrix.m00()
        val scaleY = matrix.m11()
        val clipLeft = max(nodeX, nodeX + clipFrame.left * scaleX)
        val clipTop = max(nodeY, nodeY + clipFrame.top * scaleY)
        val clipRight = min(nodeX + widthPx * scaleX, nodeX + clipFrame.right * scaleX)
        val clipBottom = min(nodeY + font.lineHeight * scaleY, nodeY + clipFrame.bottom * scaleY)
        if (clipRight <= clipLeft || clipBottom <= clipTop) return
        g.enableScissor(clipLeft.toInt(), clipTop.toInt(), clipRight.toInt(), clipBottom.toInt())
        try {
            drawText(g, font, text, widthPx, color)
        } finally {
            g.flush()
            g.disableScissor()
        }
    }

    private fun drawText(g: GuiGraphics, font: Font, text: Component, widthPx: Int, color: Int) {
        if (widthPx < font.width(text)) {
            g.drawString(font, font.plainSubstrByWidth(text.getString(), widthPx), 0, 0, color)
        } else {
            g.drawString(font, text, 0, 0, color)
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)
        advanceScroll()
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    /**
     * Advances the wheel-scroll smoothing by one step per rendered frame. Runs before
     * [ComposeScreen.render] so the snapshot apply / measure / draw of the same frame pick up the
     * new [scrollDisplay] value: animation refresh rate == game frame rate, with no coroutine lag.
     * When [scrollTarget] is already reached no state is written and nothing recomposes.
     */
    private fun advanceScroll() {
        val now = System.nanoTime()
        if (lastFrameNanos >= 0L) {
            val dt = (now - lastFrameNanos) / 1_000_000_000f
            val diff = scrollTarget - scrollDisplay
            if (abs(diff) > 0.01f) {
                val factor = 1f - exp(-dt / SCROLL_SMOOTHING_TIME)
                scrollDisplay += diff * factor
                if (abs(scrollDisplay - scrollTarget) < 0.5f) scrollDisplay = scrollTarget
            }
        }
        lastFrameNanos = now
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        // Ctrl+wheel is reserved for zoom; never intercept it as a scroll.
        if (hasControlDown()) return super.mouseScrolled(mouseX, mouseY, delta)
        val scale = uiScaleFactor()
        val lw = (width / scale).toInt()
        val lh = (height / scale).toInt()
        val panelLeft = (lw - ItemDetailsLayout.WIDTH) / 2
        val panelTop = (lh - ItemDetailsLayout.HEIGHT) / 2
        val left = panelLeft + ItemDetailsLayout.PADDING
        val top = panelTop + ItemDetailsLayout.CONTENT_TOP
        val right = left + ItemDetailsLayout.CONTENT_WIDTH
        val bottom = top + ItemDetailsLayout.CONTENT_HEIGHT
        val sx = mouseX / scale
        val sy = mouseY / scale
        if (sx in left.toDouble()..right.toDouble() && sy in top.toDouble()..bottom.toDouble()) {
            scrollTarget = (scrollTarget - delta.toFloat() * SCROLL_WHEEL_STEP).coerceIn(0f, maxScroll.toFloat())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, delta)
    }

    override fun onClose() {
        super.onClose()
        val previous = ItemDetailsScreen.consumeReturnScreen() ?: return
        if (previous === this) return
        if (previous is AbstractContainerScreen<*>) {
            val player = Minecraft.getInstance().player ?: return
            if (previous.getMenu() !== player.containerMenu) return
        }
        Minecraft.getInstance().setScreen(previous)
    }

    override fun isPauseScreen() = false

    private companion object {
        /** Exponential-smoothing time constant for wheel scroll (seconds). Lower = snappier. */
        const val SCROLL_SMOOTHING_TIME = 0.06f

        /** Wheel notch -> scroll pixels (about 2 lines per notch). */
        const val SCROLL_WHEEL_STEP = 20f
    }
}
