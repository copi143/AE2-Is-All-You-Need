package ae2x.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import appeng.client.gui.StackWithBounds
import appeng.menu.AEBaseMenu
import appeng.menu.SlotSemantic
import net.minecraft.client.renderer.Rect2i
import net.minecraft.world.inventory.Slot
import kotlin.math.roundToInt

interface AeComposeHost {
    val menu: AEBaseMenu
    val uiScale: Float
    fun bindSlot(slot: Slot, coordinates: LayoutCoordinates)
    fun hideSlot(slot: Slot)
    fun reportPanel(left: Int, top: Int, width: Int, height: Int)
    fun addExclusion(x: Int, y: Int, width: Int, height: Int)
    fun reportHoverStack(stack: StackWithBounds?)
}

val LocalAeHost = compositionLocalOf<AeComposeHost> { error("No AeComposeHost provided") }

@Composable
fun Modifier.aeMenuSlot(slot: Slot): Modifier {
    val host = LocalAeHost.current
    DisposableEffect(slot) {
        onDispose { host.hideSlot(slot) }
    }
    return onGloballyPositioned { coords -> host.bindSlot(slot, coords) }
}

@Composable
fun Modifier.aePanelBounds(): Modifier {
    val host = LocalAeHost.current
    return onGloballyPositioned { coords ->
        val pos = coords.positionInWindow()
        val scale = host.uiScale
        val left = (pos.x * scale).roundToInt()
        val top = (pos.y * scale).roundToInt()
        val width = (coords.size.width * scale).roundToInt()
        val height = (coords.size.height * scale).roundToInt()
        host.reportPanel(left, top, width, height)
        host.addExclusion(left, top, width, height)
    }
}

fun SlotSemantic.slotsOf(menu: AEBaseMenu): List<Slot> = menu.getSlots(this)
