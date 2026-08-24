package minecraftx.compose.dock

import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.rememberFrameCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme
import java.awt.Cursor

@Composable
fun McDockHost(
    state: DockState,
    onStateChange: (DockState) -> Unit,
    modifier: Modifier = Modifier,
    titleOf: (String) -> String = { it },
    content: @Composable (tabId: String) -> Unit,
) {
    val layouts = remember { mutableStateMapOf<String, DockLeafLayout>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var tick by remember { mutableIntStateOf(0) }
    val mouse = LocalMousePosition.current
    rememberFrameCallback { if (dragging != null) tick++ }
    if (dragging != null) tick
    val pointer = mouse.position
    val drop = dragging?.let {
        hitDockDrop(layouts.values.toList(), pointer.x.toFloat(), pointer.y.toFloat())
    }
    val colors = McTheme.colors

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOrigin = it.positionInWindow() }
            .then(
                if (dragging != null) Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
                else Modifier,
            ),
    ) {
        DockTree(
            node = state.root,
            state = state,
            onStateChange = onStateChange,
            layouts = layouts,
            titleOf = titleOf,
            dragging = dragging,
            onDragStart = { dragging = it },
            onDrag = {},
            onDragEnd = { tabId ->
                val target = hitDockDrop(layouts.values.toList(), mouse.position.x.toFloat(), mouse.position.y.toFloat())
                if (target != null) onStateChange(state.moveTab(tabId, target))
                dragging = null
            },
            content = content,
        )
        if (dragging != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .drawBehind {
                        val target = drop ?: return@drawBehind
                        val layout = layouts[dropLeafId(target)] ?: return@drawBehind
                        val rect = dropHighlight(layout, target) ?: return@drawBehind
                        val origin = Offset(rect.x - hostOrigin.x, rect.y - hostOrigin.y)
                        val sz = Size(rect.width, rect.height)
                        drawRect(colors.tabIndicator.copy(alpha = 0.38f), origin, sz)
                        drawRect(colors.tabIndicator, origin, sz, style = Stroke(1f))
                    },
            )
            Column(
                Modifier
                    .offset {
                        IntOffset(
                            pointer.x - hostOrigin.x.toInt() + 12,
                            pointer.y - hostOrigin.y.toInt() + 10,
                        )
                    }
                    .zIndex(2f)
                    .background(colors.panelBackground)
                    .drawBehind { drawRect(colors.tabIndicator, style = Stroke(1f)) }
                    .padding(7.dp, 4.dp),
            ) {
                McText(titleOf(dragging!!))
                McText(
                    drop?.let { dropHint(it) } ?: "拖到面板边缘或标签栏",
                    color = colors.textSecondary.toArgb(),
                )
            }
        }
    }
}

@Composable
private fun DockTree(
    node: DockNode,
    state: DockState,
    onStateChange: (DockState) -> Unit,
    layouts: MutableMap<String, DockLeafLayout>,
    titleOf: (String) -> String,
    dragging: String?,
    onDragStart: (String) -> Unit,
    onDrag: (String) -> Unit,
    onDragEnd: (String) -> Unit,
    content: @Composable (tabId: String) -> Unit,
) {
    when (node) {
        is DockNode.Split -> {
            var size by remember(node.id) { mutableStateOf(IntSize.Zero) }
            val splitModifier = Modifier.fillMaxSize().onSizeChanged { size = it }
            if (node.axis == DockAxis.Horizontal) {
                Row(splitModifier) {
                    Box(Modifier.weight(node.ratio.coerceIn(DockState.MIN_RATIO, DockState.MAX_RATIO))) {
                        DockTree(node.first, state, onStateChange, layouts, titleOf, dragging, onDragStart, onDrag, onDragEnd, content)
                    }
                    McSplitter(axis = DockAxis.Horizontal, onDrag = { delta ->
                        if (size.width > 0) {
                            onStateChange(state.setRatio(node.id, node.ratio + delta / size.width))
                        }
                    })
                    Box(Modifier.weight((1f - node.ratio).coerceIn(DockState.MIN_RATIO, DockState.MAX_RATIO))) {
                        DockTree(node.second, state, onStateChange, layouts, titleOf, dragging, onDragStart, onDrag, onDragEnd, content)
                    }
                }
            } else {
                Column(splitModifier) {
                    Box(Modifier.weight(node.ratio.coerceIn(DockState.MIN_RATIO, DockState.MAX_RATIO))) {
                        DockTree(node.first, state, onStateChange, layouts, titleOf, dragging, onDragStart, onDrag, onDragEnd, content)
                    }
                    McSplitter(axis = DockAxis.Vertical, onDrag = { delta ->
                        if (size.height > 0) {
                            onStateChange(state.setRatio(node.id, node.ratio + delta / size.height))
                        }
                    })
                    Box(Modifier.weight((1f - node.ratio).coerceIn(DockState.MIN_RATIO, DockState.MAX_RATIO))) {
                        DockTree(node.second, state, onStateChange, layouts, titleOf, dragging, onDragStart, onDrag, onDragEnd, content)
                    }
                }
            }
        }
        is DockNode.Leaf -> {
            DisposableEffect(node.id) {
                onDispose { layouts.remove(node.id) }
            }
            val tabBarHeight = McTheme.shapes.tabHeight.value
            Column(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        layouts[node.id] = DockLeafLayout(
                            id = node.id,
                            x = pos.x,
                            y = pos.y,
                            width = coords.size.width.toFloat(),
                            height = coords.size.height.toFloat(),
                            tabCount = node.tabs.size,
                            tabBarHeight = tabBarHeight,
                        )
                    },
            ) {
                McDockTabBar(
                    tabs = node.tabs,
                    active = node.active,
                    titleOf = titleOf,
                    dragging = dragging,
                    onSelect = { onStateChange(state.selectTab(node.id, it)) },
                    onClose = { onStateChange(state.closeTab(it)) },
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
                Box(Modifier.weight(1f).fillMaxSize()) {
                    val active = node.active
                    if (active != null) content(active)
                }
            }
        }
    }
}

private fun dropLeafId(drop: DockDrop): String = when (drop) {
    is DockDrop.TabBar -> drop.leafId
    is DockDrop.Center -> drop.leafId
    is DockDrop.Edge -> drop.leafId
}
