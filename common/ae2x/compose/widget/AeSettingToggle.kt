package ae2x.compose.widget

import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.LocalTooltipHost
import allyouneed.client.compose.platform.LocalUiScale
import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.compose.platform.rememberFrameCallback
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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import appeng.api.config.Setting
import appeng.api.config.SortDir
import appeng.api.config.SortOrder
import appeng.api.config.TypeFilter
import appeng.api.config.ViewItems
import appeng.api.util.IConfigurableObject
import appeng.client.gui.Icon
import appeng.core.sync.network.NetworkHandler
import appeng.core.sync.packets.ConfigValuePacket
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.network.chat.Component

private const val ICON = 16

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Enum<T>> AeSettingToggle(
    setting: Setting<T>,
    value: T,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.name },
    onCycle: ((next: T) -> Unit)? = null,
) {
    val values = setting.values.toList()
    val latest = rememberUpdatedState(value)
    val latestCycle = rememberUpdatedState(onCycle)
    val icon = iconOf(value)
    val tooltipHost = LocalTooltipHost.current
    val mouse = LocalMousePosition.current
    val density = LocalDensity.current
    val uiScale = LocalUiScale.current
    var nodePos by remember { mutableStateOf(Offset.Zero) }
    val colors = McTheme.colors

    DisposableEffect(tooltipHost, value, uiScale) {
        val unregister = tooltipHost.register {
            val graphics = McGraphics.current ?: return@register
            val p = mouse.inDensity(density)
            if (p.x !in nodePos.x.toInt()..(nodePos.x + ICON).toInt() ||
                p.y !in nodePos.y.toInt()..(nodePos.y + ICON).toInt()
            ) {
                return@register
            }
            val tip = ClientTooltipComponent.create(Component.literal(label(latest.value)).visualOrderText)
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
            .pointerInput(setting) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        change.consume()
                        cycle(
                            setting,
                            latest.value,
                            values,
                            backwards = event.button == PointerButton.Secondary,
                            latestCycle.value,
                        )
                    }
                }
            }
            .drawBehind {
                val graphics = McGraphics.current ?: return@drawBehind
                graphics.blit(Icon.TEXTURE, 0, 0, Icon.TOOLBAR_BUTTON_BACKGROUND.x, Icon.TOOLBAR_BUTTON_BACKGROUND.y, ICON, ICON)
                if (icon != null) {
                    graphics.blit(Icon.TEXTURE, 0, 0, icon.x, icon.y, icon.width, icon.height)
                }
                val p = mouse.inDensity(density)
                if (p.x in nodePos.x.toInt()..(nodePos.x + ICON).toInt() &&
                    p.y in nodePos.y.toInt()..(nodePos.y + ICON).toInt()
                ) {
                    drawRect(color = colors.slotHoverOverlay)
                }
            },
    )
}

@Composable
fun <T : Enum<T>> AeSettingToggle(
    host: IConfigurableObject,
    setting: Setting<T>,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.name },
) {
    var value by remember(setting) { mutableStateOf(host.configManager.getSetting(setting)) }
    rememberFrameCallback {
        val live = host.configManager.getSetting(setting)
        if (value != live) value = live
    }
    AeSettingToggle(
        setting = setting,
        value = value,
        modifier = modifier,
        label = label,
        onCycle = { next ->
            value = next
            NetworkHandler.instance().sendToServer(ConfigValuePacket(setting, next))
        },
    )
}

private fun iconOf(value: Enum<*>): Icon? = when (value) {
    SortOrder.NAME -> Icon.SORT_BY_NAME
    SortOrder.AMOUNT -> Icon.SORT_BY_AMOUNT
    SortOrder.MOD -> Icon.SORT_BY_MOD
    SortDir.ASCENDING -> Icon.ARROW_UP
    SortDir.DESCENDING -> Icon.ARROW_DOWN
    ViewItems.STORED -> Icon.VIEW_MODE_STORED
    ViewItems.ALL -> Icon.VIEW_MODE_ALL
    ViewItems.CRAFTABLE -> Icon.VIEW_MODE_CRAFTING
    TypeFilter.ALL -> Icon.TYPE_FILTER_ALL
    TypeFilter.ITEMS -> Icon.TYPE_FILTER_ITEMS
    TypeFilter.FLUIDS -> Icon.TYPE_FILTER_FLUIDS
    else -> null
}

private fun <T : Enum<T>> cycle(
    setting: Setting<T>,
    value: T,
    values: List<T>,
    backwards: Boolean,
    onCycle: ((T) -> Unit)?,
) {
    if (values.isEmpty()) return
    val index = values.indexOf(value).let { if (it < 0) 0 else it }
    val next = if (backwards) {
        values[(index - 1 + values.size) % values.size]
    } else {
        values[(index + 1) % values.size]
    }
    if (onCycle != null) onCycle(next)
    else NetworkHandler.instance().sendToServer(ConfigValuePacket(setting, next))
}
