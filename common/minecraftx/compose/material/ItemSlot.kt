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
import minecraftx.compose.geometry.PanelGeometry
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

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
    tooltipLines: () -> List<Component> = { emptyList() },
    paintStack: ((GuiGraphics, Int, Int) -> Unit)? = null,
    colors: McColorScheme = McTheme.colors,
) {
    val renderer = remember { SlotRenderers.get() }
    val latestClick = rememberUpdatedState(onSlotClicked)
    val latestStack = rememberUpdatedState(stack)
    val latestAmount = rememberUpdatedState(amount)
    val latestCraftable = rememberUpdatedState(craftable)
    val latestTooltip = rememberUpdatedState(tooltipLines)
    val latestPaint = rememberUpdatedState(paintStack)
    val tooltipHost = LocalTooltipHost.current
    val uiScale = LocalUiScale.current
    val mouse = LocalMousePosition.current
    val density = LocalDensity.current
    val slotSize = McTheme.shapes.slotSize.value.roundToInt().coerceAtLeast(1)
    var nodePos by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(tooltipHost, uiScale, showTooltip, slotSize) {
        if (!showTooltip) return@DisposableEffect onDispose { }
        val unregister = tooltipHost.register {
            val graphics = McGraphics.current ?: return@register
            val carried = Minecraft.getInstance().player?.containerMenu?.carried
            if (carried != null && !carried.isEmpty) return@register
            val p = mouse.inDensity(density)
            if (!PanelGeometry.containsHalfOpen(p.x, p.y, nodePos.x, nodePos.y, slotSize)) return@register
            val extra = latestTooltip.value()
            val anchor = Offset(p.x.toFloat(), p.y.toFloat()) * uiScale
            if (extra.isNotEmpty()) {
                val tips = extra.map { ClientTooltipComponent.create(it.visualOrderText) }
                graphics.renderMcTooltip(
                    Minecraft.getInstance().font,
                    tips,
                    (anchor.x + 10).toInt(),
                    (anchor.y - 8).toInt(),
                )
                return@register
            }
            val held = latestStack.value()
            if (held.isEmpty) return@register
            graphics.renderTooltip(
                Minecraft.getInstance().font,
                held,
                anchor.x.toInt(),
                anchor.y.toInt(),
            )
        }
        onDispose { unregister() }
    }

    Box(
        modifier = modifier
            .size(slotSize.dp)
            .onGloballyPositioned { nodePos = it.positionInWindow() }
            .then(
                if (!consumeClicks) Modifier
                else Modifier.pointerInput(interactive, renderer, slotSize) {
                    var gestureButton: PointerButton? = null
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position
                            val inBounds = pos.x >= 0f && pos.x < slotSize && pos.y >= 0f && pos.y < slotSize
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
                val painter = latestPaint.value
                if (painter != null) painter(graphics, 1, 1) else renderer.drawStack(graphics, held, 1, 1)
                if (disabled) drawRect(color = colors.slotDisabledOverlay)
                if (missing) drawRect(color = colors.slotMissingOverlay)
                if (!qty.isNullOrEmpty()) {
                    val font = Minecraft.getInstance().font
                    graphics.pose().pushPose()
                    graphics.pose().translate(1f, 1f, 200f)
                    graphics.pose().scale(0.5f, 0.5f, 1f)
                    val textX = slotSize * 2 - 2 - font.width(qty)
                    val textY = slotSize * 2 - 2 - font.lineHeight
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
                if (PanelGeometry.containsHalfOpen(p.x, p.y, nodePos.x, nodePos.y, slotSize)) {
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
    tooltipLines: List<Component> = emptyList(),
    paintStack: ((GuiGraphics, Int, Int) -> Unit)? = null,
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
        tooltipLines = { tooltipLines },
        paintStack = paintStack,
        colors = colors,
    )
}

private fun mouseButtonOf(button: PointerButton?): Int = when (button) {
    PointerButton.Secondary -> 1
    PointerButton.Tertiary -> 2
    else -> 0
}

@OptIn(ExperimentalComposeUiApi::class)
private fun clickTypeOf(event: PointerEvent): ClickType {
    val button = mouseButtonOf(event.button)
    if (Minecraft.getInstance().options.keyPickItem.matchesMouse(button)) return ClickType.CLONE
    return when {
        event.keyboardModifiers.isShiftPressed -> ClickType.QUICK_MOVE
        else -> ClickType.PICKUP
    }
}

interface ItemSlotRenderer {
    fun drawStack(graphics: GuiGraphics, stack: ItemStack, x: Int, y: Int)
    fun onClick(stack: ItemStack, button: Int)
}

object SlotRenderers {
    private var cached: ItemSlotRenderer? = null
    private var cachedEmi: Boolean? = null

    fun get(): ItemSlotRenderer {
        val emi = hasEmi()
        val current = cached
        if (current != null && cachedEmi == emi) return current
        val renderer = if (emi) EmiSlotRenderer() else VanillaSlotRenderer()
        cached = renderer
        cachedEmi = emi
        return renderer
    }

    private fun hasEmi(): Boolean =
        runCatching { Class.forName("dev.emi.emi.api.EmiApi") }.isSuccess
}
