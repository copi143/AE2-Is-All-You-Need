package ae2x.compose.slot

import ae2x.compose.AeSlotGeometry
import ae2x.compose.LocalAeHost
import ae2x.compose.aeMenuSlot
import ae2x.compose.format.AeAmountFormat
import ae2x.compose.rememberGuiSync
import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.LocalUiScale
import allyouneed.util.bigint.BigAmounts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import appeng.api.client.AEKeyRendering
import appeng.api.stacks.GenericStack
import appeng.client.gui.StackWithBounds
import appeng.client.gui.me.common.Repo
import appeng.client.gui.me.common.RepoSlot
import appeng.core.localization.GuiText
import appeng.menu.me.common.GridInventoryEntry
import minecraftx.compose.geometry.PanelGeometry
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Rect2i
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

@Composable
fun AeRepoGrid(
    repo: Repo,
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    viewOnlyCraftable: Boolean = false,
    onEntryClick: (GridInventoryEntry?, button: Int, clickType: ClickType) -> Unit,
) {
    if (columns <= 0 || rows <= 0) return
    val slotSize = McTheme.shapes.slotSize
    val slotPx = with(LocalDensity.current) { slotSize.toPx() }
    val visible = rows * columns
    val host = LocalAeHost.current
    val mouse = LocalMousePosition.current
    val uiScale = LocalUiScale.current
    val density = LocalDensity.current
    val powered = rememberGuiSync { repo.hasPower() }
    var gridPos by remember { mutableStateOf(Offset.Zero) }
    val repoSlots = host.menu.slots.filterIsInstance<RepoSlot>()
    Box(
        modifier
            .size(slotSize * columns, slotSize * rows)
            .onGloballyPositioned { gridPos = it.positionInWindow() },
    ) {
        val pointer = mouse.inDensity(density)
        repeat(visible) { index ->
            val entry = repo.get(index)
            val col = index % columns
            val row = index / columns
            val cellX = gridPos.x + col * slotPx
            val cellY = gridPos.y + row * slotPx
            if (PanelGeometry.containsHalfOpen(pointer.x, pointer.y, cellX, cellY, slotPx.roundToInt())) {
                val what = entry?.what
                if (what != null) {
                    host.reportHoverStack(
                        StackWithBounds(
                            GenericStack(what, entry.storedAmount),
                            Rect2i(
                                (cellX * uiScale).roundToInt() + AeSlotGeometry.scaledInset(uiScale),
                                (cellY * uiScale).roundToInt() + AeSlotGeometry.scaledInset(uiScale),
                                AeSlotGeometry.scaledItemSize(uiScale),
                                AeSlotGeometry.scaledItemSize(uiScale),
                            ),
                        ),
                    )
                }
            }
            val slot = repoSlots.getOrNull(index)
            ItemSlot(
                stack = { repo.get(index)?.what?.wrapForDisplayOrFilter() ?: ItemStack.EMPTY },
                modifier = Modifier
                    .offset(slotSize * col, slotSize * row)
                    .then(if (slot != null) Modifier.aeMenuSlot(slot) else Modifier),
                interactive = true,
                // Repo 格必须由 Compose 直接消费点击并走 handleRepoClick：
                // 原版 findSlot 依赖 slot.x/y 异步定位（resize 后会 HIDDEN、首帧未定位等），
                // 一旦 consumeClicks=false，切到原版 slotClicked 链路就会点不上、无法取出。
                // 这里保留 aeMenuSlot 绑定（供悬停/JEI/滚轮 findSlot 用），但点击始终由 Compose 处理。
                consumeClicks = true,
                amount = {
                    val current = repo.get(index)
                    if (current == null) {
                        null
                    } else {
                        val stored = BigAmounts.getEntryAmount(current)
                        val craftable = current.isCraftable
                        when {
                            craftable && (viewOnlyCraftable || stored.signum() <= 0) -> GuiText.SmallFontCraft.getLocal()
                            stored.signum() > 0 -> AeAmountFormat.slot(stored)
                            else -> null
                        }
                    }
                },
                craftable = {
                    val current = repo.get(index)
                    current != null && current.isCraftable &&
                        BigAmounts.getEntryAmount(current).signum() > 0 && !viewOnlyCraftable
                },
                disabled = !powered,
                paintStack = { graphics, x, y ->
                    val what = repo.get(index)?.what
                    if (what != null) {
                        AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x, y, what)
                    }
                },
                onSlotClicked = { button, clickType -> onEntryClick(repo.get(index), button, clickType) },
            )
        }
    }
}

private operator fun androidx.compose.ui.unit.Dp.times(count: Int) = this * count.toFloat()
