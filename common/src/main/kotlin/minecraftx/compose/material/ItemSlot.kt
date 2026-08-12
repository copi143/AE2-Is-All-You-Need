package minecraftx.compose.material

import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.LocalTooltipHost
import allyouneed.client.compose.platform.LocalUiScale
import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.renderMcTooltip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack

/**
 * A grid slot rendered by the Compose tree. The look (slot background, item icon, hover highlight)
 * matches vanilla containers; interaction depends on the mode:
 *
 *  - [interactive] = false (virtual): the slot shows [stack] and offers full EMI interaction
 *    (hover tooltip, click to view recipes / uses). Without EMI it falls back to the vanilla tooltip
 *    and JEI recipe lookup.
 *  - [interactive] = true: the slot is bound to a real container [ItemStack] and clicks are handed
 *    to [onSlotClicked] so the host can perform vanilla take/place logic.
 *
 * Tooltips are drawn as a floating layer via [LocalTooltipHost] after the tree has been drawn, so
 * they always paint on top. Hover is decided geometrically (mouse position vs. the node's current
 * bounds read at draw time) rather than from the pointer enter/exit state machine, which can miss
 * the exit on some move paths and leave the tooltip stuck.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ItemSlot(
    stack: ItemStack,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    onSlotClicked: ((button: Int, clickType: ClickType) -> Unit)? = null,
    colors: McColorScheme = McTheme.colors,
) {
    val renderer = remember { SlotRenderers.get() }
    val tooltipHost = LocalTooltipHost.current
    val uiScale = LocalUiScale.current
    val mouse = LocalMousePosition.current
    val density = LocalDensity.current
    var nodePos by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(tooltipHost, renderer, stack, uiScale) {
        val unregister = tooltipHost.register {
            val graphics = McGraphics.current ?: return@register
            if (stack.isEmpty) return@register
            // Authoritative geometric hover check: the pointer's logical position against the
            // slot's current bounds, independent of the enter/exit state machine.
            val p = mouse.inDensity(density)
            if (p.x !in nodePos.x.toInt()..(nodePos.x + SLOT_SIZE).toInt() ||
                p.y !in nodePos.y.toInt()..(nodePos.y + SLOT_SIZE).toInt()
            ) {
                return@register
            }
            val tooltip = renderer.getTooltip(stack)
            if (tooltip.isEmpty()) return@register
            // mouse/inDensity are in logical root space; the tooltip draws on the raw GuiGraphics
            // in screen pixels, so multiply back by the zoom factor.
            val anchor = Offset(p.x.toFloat(), p.y.toFloat()) * uiScale
            graphics.renderMcTooltip(
                Minecraft.getInstance().font,
                tooltip,
                (anchor.x + 10).toInt(),
                (anchor.y - 8).toInt(),
            )
        }
        onDispose { unregister() }
    }

    Box(
        modifier = modifier
            .size(SLOT_SIZE.dp)
            .onGloballyPositioned { nodePos = it.positionInWindow() }
            .pointerInput(stack, interactive, renderer) {
                var gestureButton: PointerButton? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val pos = change.position
                        val inBounds = pos.x in 0f..SLOT_SIZE.toFloat() && pos.y in 0f..SLOT_SIZE.toFloat()
                        when (event.type) {
                            PointerEventType.Press -> gestureButton = event.button
                            PointerEventType.Release -> {
                                if (gestureButton != null && inBounds) {
                                    val button = if (gestureButton == PointerButton.Secondary) 1 else 0
                                    if (interactive) {
                                        onSlotClicked?.invoke(button, ClickType.PICKUP)
                                    } else {
                                        renderer.onClick(stack, button)
                                    }
                                }
                                gestureButton = null
                            }
                            else -> Unit
                        }
                    }
                }
            }
            .drawBehind {
                val graphics = McGraphics.current ?: return@drawBehind
                drawRect(color = colors.slotBackground)
                drawRect(color = colors.slotBorder, style = Stroke(1f))
                renderer.drawStack(graphics, stack, 1, 1)
                val p = mouse.inDensity(density)
                if (p.x in nodePos.x.toInt()..(nodePos.x + SLOT_SIZE).toInt() &&
                    p.y in nodePos.y.toInt()..(nodePos.y + SLOT_SIZE).toInt()
                ) {
                    drawRect(color = colors.slotHoverOverlay)
                }
            },
    )
}

private const val SLOT_SIZE = 18

/**
 * Renders a stack inside an [ItemSlot] and drives its tooltip / click behaviour. The concrete
 * implementation is chosen at runtime: EMI when installed, otherwise the vanilla renderer with JEI
 * recipe lookup.
 */
interface ItemSlotRenderer {
    fun drawStack(graphics: GuiGraphics, stack: ItemStack, x: Int, y: Int)
    fun getTooltip(stack: ItemStack): List<ClientTooltipComponent>
    fun onClick(stack: ItemStack, button: Int)
}

object SlotRenderers {
    private var cached: ItemSlotRenderer? = null

    fun get(): ItemSlotRenderer = cached ?: run {
        val renderer = if (hasEmi()) EmiSlotRenderer() else VanillaSlotRenderer()
        cached = renderer
        renderer
    }

    private fun hasEmi(): Boolean =
        runCatching { Class.forName("dev.emi.emi.api.EmiApi") }.isSuccess
}
