package ae2x.compose.widget

import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.LocalTooltipHost
import allyouneed.client.compose.platform.LocalUiScale
import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.rememberFrameCallback
import allyouneed.client.compose.platform.renderMcTooltip
import allyouneed.util.rl
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import appeng.api.config.Settings
import appeng.api.config.SortDir
import appeng.api.config.SortOrder
import appeng.api.util.IConfigurableObject
import appeng.client.gui.Icon
import appeng.client.gui.style.Blitter
import appeng.core.sync.network.NetworkHandler
import appeng.core.sync.packets.ConfigValuePacket
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.network.chat.Component

private const val ICON = 16
private const val TEX = 64

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AeSortToggle(
    host: IConfigurableObject,
    modifier: Modifier = Modifier,
) {
    var order by remember { mutableStateOf(host.configManager.getSetting(Settings.SORT_BY)) }
    var dir by remember { mutableStateOf(host.configManager.getSetting(Settings.SORT_DIRECTION)) }
    rememberFrameCallback {
        val liveOrder = host.configManager.getSetting(Settings.SORT_BY)
        val liveDir = host.configManager.getSetting(Settings.SORT_DIRECTION)
        if (order != liveOrder) order = liveOrder
        if (dir != liveDir) dir = liveDir
    }
    val latestOrder = rememberUpdatedState(order)
    val latestDir = rememberUpdatedState(dir)
    val orders = Settings.SORT_BY.values.toList()
    val tooltipHost = LocalTooltipHost.current
    val mouse = LocalMousePosition.current
    val density = LocalDensity.current
    val uiScale = LocalUiScale.current
    var nodePos by remember { mutableStateOf(Offset.Zero) }
    val tint = McTheme.colors.textPrimary.toArgb()
    val hover = McTheme.colors.slotHoverOverlay
    val texture = sortTexture(order, dir)

    DisposableEffect(tooltipHost, order, dir, uiScale) {
        val unregister = tooltipHost.register {
            val graphics = McGraphics.current ?: return@register
            val p = mouse.inDensity(density)
            if (p.x !in nodePos.x.toInt()..(nodePos.x + ICON).toInt() ||
                p.y !in nodePos.y.toInt()..(nodePos.y + ICON).toInt()
            ) {
                return@register
            }
            val text = "${latestOrder.value.name} / ${latestDir.value.name}"
            val tip = ClientTooltipComponent.create(Component.literal(text).visualOrderText)
            val anchor = Offset(p.x.toFloat(), p.y.toFloat()) * uiScale
            graphics.renderMcTooltip(
                Minecraft.getInstance().font,
                listOf(tip),
                (anchor.x + 10).toInt(),
                (anchor.y - 8).toInt(),
            )
        }
        onDispose { unregister() }
    }

    Box(
        modifier
            .size(ICON.dp)
            .onGloballyPositioned { nodePos = it.positionInWindow() }
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        change.consume()
                        if (event.button == PointerButton.Secondary) {
                            val next = if (latestDir.value == SortDir.ASCENDING) SortDir.DESCENDING else SortDir.ASCENDING
                            dir = next
                            NetworkHandler.instance().sendToServer(ConfigValuePacket(Settings.SORT_DIRECTION, next))
                        } else {
                            val index = orders.indexOf(latestOrder.value).let { if (it < 0) 0 else it }
                            val next = orders[(index + 1) % orders.size]
                            order = next
                            NetworkHandler.instance().sendToServer(ConfigValuePacket(Settings.SORT_BY, next))
                        }
                    }
                }
            }
            .drawBehind {
                val graphics = McGraphics.current ?: return@drawBehind
                graphics.blit(Icon.TEXTURE, 0, 0, Icon.TOOLBAR_BUTTON_BACKGROUND.x, Icon.TOOLBAR_BUTTON_BACKGROUND.y, ICON, ICON)
                Blitter.texture(texture, TEX, TEX)
                    .src(0, 0, TEX, TEX)
                    .dest(0, 0, ICON, ICON)
                    .colorArgb(tint)
                    .blit(graphics)
                val p = mouse.inDensity(density)
                if (p.x in nodePos.x.toInt()..(nodePos.x + ICON).toInt() &&
                    p.y in nodePos.y.toInt()..(nodePos.y + ICON).toInt()
                ) {
                    drawRect(color = hover)
                }
            },
    )
}

private fun sortTexture(order: SortOrder, dir: SortDir) =
    "textures/guis/sort_${order.name.lowercase()}_${dir.name.lowercase()}.png".rl
