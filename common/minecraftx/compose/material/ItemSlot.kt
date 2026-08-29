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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
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
 * A grid slot rendered by the Compose tree. [stack] / [amount] / [craftable] are read at draw time
 * so container GUIs stay live without a recomposition.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ItemSlot(
    stack: () -> ItemStack,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    consumeClicks: Boolean = true,
    amount: () -> String? = { null },
    craftable: () -> Boolean = { false },
    disabled: Boolean = false,
    missing: Boolean = false,
    showTooltip: Boolean = true,
    onSlotClicked: ((button: Int, clickType: ClickType) -> Unit)? = null,
    colors: McColorScheme = McTheme.colors,
) {
    val renderer = remember { SlotRenderers.get() }
    val latestClick = rememberUpdatedState(onSlotClicked)
    val latestStack = rememberUpdatedState(stack)
    val latestAmount = rememberUpdatedState(amount)
    val latestCraftable = rememberUpdatedState(craftable)
    val tooltipHost = LocalTooltipHost.current
    val uiScale = LocalUiScale.current
    val mouse = LocalMousePosition.current
    val density = LocalDensity.current
    var nodePos by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(tooltipHost, renderer, uiScale, showTooltip) {
        if (!showTooltip) return@DisposableEffect onDispose { }
        val unregister = tooltipHost.register {
            val graphics = McGraphics.current ?: return@register
            val held = latestStack.value()
            if (held.isEmpty) return@register
            val p = mouse.inDensity(density)
            if (p.x !in nodePos.x.toInt()..(nodePos.x + SLOT_SIZE).toInt() ||
                p.y !in nodePos.y.toInt()..(nodePos.y + SLOT_SIZE).toInt()
            ) {
                return@register
            }
            val tooltip = renderer.getTooltip(held)
            if (tooltip.isEmpty()) return@register
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
            .then(
                if (!consumeClicks) Modifier
                else Modifier.pointerInput(interactive, renderer) {
                    var gestureButton: PointerButton? = null
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position
                            val inBounds = pos.x in 0f..SLOT_SIZE.toFloat() && pos.y in 0f..SLOT_SIZE.toFloat()
                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (change.isConsumed) continue
                                    gestureButton = event.button
                                    change.consume()
                                    if (interactive) {
                                        latestClick.value?.invoke(mouseButtonOf(gestureButton), clickTypeOf(event))
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (!interactive && gestureButton != null && inBounds && !change.isConsumed) {
                                        renderer.onClick(latestStack.value(), mouseButtonOf(gestureButton))
                                        change.consume()
                                    }
                                    gestureButton = null
                                }
                                else -> Unit
                            }
                        }
                    }
                },
            )
            .drawBehind {
                val graphics = McGraphics.current ?: return@drawBehind
                val held = latestStack.value()
                val qty = latestAmount.value()
                drawRect(color = colors.slotBackground)
                drawRect(color = colors.slotBorder, style = Stroke(1f))
                renderer.drawStack(graphics, held, 1, 1)
                if (disabled) drawRect(color = colors.slotDisabledOverlay)
                if (missing) drawRect(color = colors.slotMissingOverlay)
                if (!qty.isNullOrEmpty()) {
                    val font = Minecraft.getInstance().font
                    graphics.pose().pushPose()
                    graphics.pose().translate(1f, 1f, 200f)
                    graphics.pose().scale(0.5f, 0.5f, 1f)
                    val textX = SLOT_SIZE * 2 - 2 - font.width(qty)
                    val textY = SLOT_SIZE * 2 - 2 - font.lineHeight
                    graphics.drawString(font, qty, textX, textY, 0xFFFFFF, false)
                    graphics.pose().popPose()
                }
                if (latestCraftable.value()) {
                    val font = Minecraft.getInstance().font
                    graphics.pose().pushPose()
                    graphics.pose().translate(1f, 1f, 200f)
                    graphics.pose().scale(0.5f, 0.5f, 1f)
                    graphics.drawString(font, "+", 0, 0, 0x00FF00, false)
                    graphics.pose().popPose()
                }
                val p = mouse.inDensity(density)
                if (p.x in nodePos.x.toInt()..(nodePos.x + SLOT_SIZE).toInt() &&
                    p.y in nodePos.y.toInt()..(nodePos.y + SLOT_SIZE).toInt()
                ) {
                    drawRect(color = colors.slotHoverOverlay)
                }
            },
    )
}

@Composable
fun ItemSlot(
    stack: ItemStack,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    consumeClicks: Boolean = true,
    amount: String? = null,
    craftable: Boolean = false,
    disabled: Boolean = false,
    missing: Boolean = false,
    showTooltip: Boolean = true,
    onSlotClicked: ((button: Int, clickType: ClickType) -> Unit)? = null,
    colors: McColorScheme = McTheme.colors,
) {
    ItemSlot(
        stack = { stack },
        modifier = modifier,
        interactive = interactive,
        consumeClicks = consumeClicks,
        amount = { amount },
        craftable = { craftable },
        disabled = disabled,
        missing = missing,
        showTooltip = showTooltip,
        onSlotClicked = onSlotClicked,
        colors = colors,
    )
}

private const val SLOT_SIZE = 18

private fun mouseButtonOf(button: PointerButton?): Int = when (button) {
    PointerButton.Secondary -> 1
    PointerButton.Tertiary -> 2
    else -> 0
}

@OptIn(ExperimentalComposeUiApi::class)
private fun clickTypeOf(event: PointerEvent): ClickType = when {
    event.button == PointerButton.Tertiary -> ClickType.CLONE
    event.keyboardModifiers.isShiftPressed -> ClickType.QUICK_MOVE
    else -> ClickType.PICKUP
}

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
