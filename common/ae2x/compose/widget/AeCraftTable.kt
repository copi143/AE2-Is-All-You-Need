package ae2x.compose.widget

import ae2x.compose.rememberGuiSync
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import appeng.api.client.AEKeyRendering
import appeng.api.stacks.AEKey
import appeng.api.stacks.AmountFormat
import appeng.api.util.AEColor
import appeng.core.AEConfig
import appeng.core.localization.GuiText
import appeng.menu.me.crafting.CraftingPlanSummaryEntry
import appeng.menu.me.crafting.CraftingStatusEntry
import minecraftx.compose.foundation.McScrollBox
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

data class AeCraftRow(
    val stack: ItemStack,
    val lines: List<Component>,
    val tooltip: List<Component> = emptyList(),
    val overlay: Color = Color.Transparent,
    val background: Color = Color.Transparent,
)

@Composable
fun AeCraftTable(
    entries: List<AeCraftRow>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    visibleRows: Int = 5,
    colors: McColorScheme = McTheme.colors,
) {
    val cellW = 67
    val cellH = 22
    val rows = if (columns <= 0) 0 else (entries.size + columns - 1) / columns
    val state = rememberScrollState()
    McScrollBox(
        contentHeight = (rows * cellH).coerceAtLeast(visibleRows * cellH),
        modifier = modifier.size((cellW * columns).dp, (cellH * visibleRows).dp),
        state = state,
    ) {
        Column {
            entries.chunked(columns).forEach { row ->
                Row {
                    row.forEach { entry ->
                        AeCraftCell(entry, Modifier.size(cellW.dp, cellH.dp), colors)
                    }
                }
            }
        }
    }
}

@Composable
fun AeCraftConfirmTable(
    entries: List<CraftingPlanSummaryEntry>,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    AeCraftTable(entries.map { it.toCraftRow() }, modifier = modifier, visibleRows = 5, colors = colors)
}

@Composable
fun AeCraftingStatusTable(
    entries: List<CraftingStatusEntry>,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    AeCraftTable(entries.map { it.toCraftRow() }, modifier = modifier, visibleRows = 6, colors = colors)
}

@Composable
private fun AeCraftCell(entry: AeCraftRow, modifier: Modifier, colors: McColorScheme) {
    Row(
        modifier = modifier
            .background(if (entry.background == Color.Transparent) colors.contentBackground else entry.background)
            .drawBehind { drawRect(color = colors.contentBorder, style = Stroke(1f)) }
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            entry.lines.forEach { line ->
                McText(line, color = colors.textPrimary.toArgb(), maxWidth = 44)
            }
        }
        Box {
            ItemSlot(
                stack = entry.stack,
                consumeClicks = true,
                showTooltip = entry.tooltip.isEmpty(),
                colors = colors,
            )
            if (entry.overlay != Color.Transparent) {
                Box(Modifier.matchParentSize().background(entry.overlay))
            }
        }
    }
}

private fun CraftingPlanSummaryEntry.toCraftRow(): AeCraftRow {
    val lines = ArrayList<Component>(3)
    if (storedAmount > 0) {
        lines += GuiText.FromStorage.text(what.formatAmount(storedAmount, AmountFormat.SLOT))
    }
    if (missingAmount > 0) {
        lines += GuiText.Missing.text(what.formatAmount(missingAmount, AmountFormat.SLOT))
    }
    if (craftAmount > 0) {
        lines += GuiText.ToCraft.text(what.formatAmount(craftAmount, AmountFormat.SLOT))
    }
    val tooltip = ArrayList(AEKeyRendering.getTooltip(what))
    if (storedAmount > 0) {
        tooltip += GuiText.FromStorage.text(what.formatAmount(storedAmount, AmountFormat.FULL))
    }
    if (missingAmount > 0) {
        tooltip += GuiText.Missing.text(what.formatAmount(missingAmount, AmountFormat.FULL))
    }
    if (craftAmount > 0) {
        tooltip += GuiText.ToCraft.text(what.formatAmount(craftAmount, AmountFormat.FULL))
    }
    return AeCraftRow(
        stack = what.asDisplayStack(),
        lines = lines,
        tooltip = tooltip,
        overlay = if (missingAmount > 0) Color(0x1AFF0000) else Color.Transparent,
    )
}

private fun CraftingStatusEntry.toCraftRow(): AeCraftRow {
    val lines = ArrayList<Component>(3)
    if (storedAmount > 0) {
        lines += GuiText.FromStorage.text(what.formatAmount(storedAmount, AmountFormat.SLOT))
    }
    if (activeAmount > 0) {
        lines += GuiText.Crafting.text(what.formatAmount(activeAmount, AmountFormat.SLOT))
    }
    if (pendingAmount > 0) {
        lines += GuiText.Scheduled.text(what.formatAmount(pendingAmount, AmountFormat.SLOT))
    }
    val tooltip = ArrayList(AEKeyRendering.getTooltip(what))
    if (storedAmount > 0) {
        tooltip += GuiText.FromStorage.text(what.formatAmount(storedAmount, AmountFormat.FULL))
    }
    if (activeAmount > 0) {
        tooltip += GuiText.Crafting.text(what.formatAmount(activeAmount, AmountFormat.FULL))
    }
    if (pendingAmount > 0) {
        tooltip += GuiText.Scheduled.text(what.formatAmount(pendingAmount, AmountFormat.FULL))
    }
    val background = if (AEConfig.instance().isUseColoredCraftingStatus) {
        when {
            activeAmount > 0 -> Color(AEColor.GREEN.blackVariant or 0x5A000000)
            pendingAmount > 0 -> Color(AEColor.YELLOW.blackVariant or 0x5A000000)
            else -> Color.Transparent
        }
    } else {
        Color.Transparent
    }
    return AeCraftRow(
        stack = what.asDisplayStack(),
        lines = lines,
        tooltip = tooltip,
        background = background,
    )
}

private fun AEKey.asDisplayStack(): ItemStack = wrapForDisplayOrFilter()
