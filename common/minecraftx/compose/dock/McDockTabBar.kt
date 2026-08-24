package minecraftx.compose.dock

import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import minecraftx.compose.material.McTab
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component
import kotlin.math.hypot

private const val DRAG_SLOP = 4f

@Composable
fun McDockTabBar(
    tabs: List<String>,
    active: String?,
    titleOf: (String) -> String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onDragStart: (String) -> Unit,
    onDrag: (String) -> Unit,
    onDragEnd: (String) -> Unit,
    modifier: Modifier = Modifier,
    dragging: String? = null,
    extra: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(McTheme.shapes.tabHeight),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (tabId in tabs) {
            McTab(
                label = titleOf(tabId),
                selected = tabId == active && tabId != dragging,
                onClick = { onSelect(tabId) },
                enabled = tabId != dragging,
                handleClicks = false,
                modifier = Modifier.pointerInput(tabId) {
                    awaitPointerEventScope {
                        var pressed = false
                        var dragged = false
                        var startX = 0f
                        var startY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val p = change.position
                            when (event.type) {
                                PointerEventType.Press -> if (!change.isConsumed) {
                                    pressed = true
                                    dragged = false
                                    startX = p.x
                                    startY = p.y
                                    change.consume()
                                }
                                PointerEventType.Move -> if (pressed && !change.isConsumed) {
                                    if (!dragged && hypot(p.x - startX, p.y - startY) >= DRAG_SLOP) {
                                        dragged = true
                                        onDragStart(tabId)
                                    }
                                    if (dragged) {
                                        onDrag(tabId)
                                        change.consume()
                                    }
                                }
                                PointerEventType.Release -> if (pressed) {
                                    if (dragged) onDragEnd(tabId) else onSelect(tabId)
                                    pressed = false
                                    dragged = false
                                    if (!change.isConsumed) change.consume()
                                }
                                else -> Unit
                            }
                        }
                    }
                },
            )
            McText(
                Component.literal("×"),
                color = McTheme.colors.textSecondary.toArgb(),
                modifier = Modifier.padding(end = 4.dp).clickable { onClose(tabId) },
            )
        }
        extra()
    }
}
