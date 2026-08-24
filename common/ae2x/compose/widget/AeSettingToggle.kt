package ae2x.compose.widget

import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import appeng.api.config.Setting
import appeng.api.util.IConfigurableObject
import appeng.core.sync.network.NetworkHandler
import appeng.core.sync.packets.ConfigValuePacket
import minecraftx.compose.material.McButton
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme

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
    McButton(
        onClick = { cycle(setting, value, values, backwards = false, onCycle) },
        modifier = modifier.pointerInput(setting, value) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Release && event.button == PointerButton.Secondary) {
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        cycle(setting, value, values, backwards = true, onCycle)
                        change.consume()
                    }
                }
            }
        },
    ) {
        McText(label(value), color = McTheme.colors.textPrimary.toArgb(), maxWidth = 80)
    }
}

@Composable
fun <T : Enum<T>> AeSettingToggle(
    host: IConfigurableObject,
    setting: Setting<T>,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.name },
) {
    val value = host.configManager.getSetting(setting)
    AeSettingToggle(
        setting = setting,
        value = value,
        modifier = modifier,
        label = label,
    )
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
