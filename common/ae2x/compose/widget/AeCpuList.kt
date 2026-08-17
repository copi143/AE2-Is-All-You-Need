package ae2x.compose.widget

import ae2x.compose.format.AeAmountFormat
import ae2x.compose.rememberGuiSync
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import appeng.api.config.CpuSelectionMode
import appeng.core.localization.GuiText
import appeng.menu.me.crafting.CraftingStatusMenu
import minecraftx.compose.foundation.McScrollBox
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.material.McProgressBar
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

@Composable
fun AeCpuList(
    menu: CraftingStatusMenu,
    modifier: Modifier = Modifier,
    visibleRows: Int = 6,
    colors: McColorScheme = McTheme.colors,
) {
    val cpus = rememberGuiSync { menu.cpuList.cpus() }
    val selected = rememberGuiSync { menu.selectedCpuSerial }
    AeCpuList(
        cpus = cpus,
        selectedSerial = selected,
        onSelect = menu::selectCpu,
        modifier = modifier,
        visibleRows = visibleRows,
        colors = colors,
    )
}

@Composable
fun AeCpuList(
    cpus: List<CraftingStatusMenu.CraftingCpuListEntry>,
    selectedSerial: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleRows: Int = 6,
    colors: McColorScheme = McTheme.colors,
) {
    val rowH = 22
    val viewportH = visibleRows * rowH
    val state = rememberScrollState()
    McScrollBox(
        contentHeight = (cpus.size * rowH).coerceAtLeast(viewportH),
        modifier = modifier.size(120.dp, viewportH.dp),
        state = state,
    ) {
        Column {
            cpus.forEach { cpu ->
                AeCpuRow(
                    cpu = cpu,
                    selected = cpu.serial() == selectedSerial,
                    onSelect = { onSelect(cpu.serial()) },
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun AeCpuRow(
    cpu: CraftingStatusMenu.CraftingCpuListEntry,
    selected: Boolean,
    onSelect: () -> Unit,
    colors: McColorScheme,
) {
    val job = cpu.currentJob()
    val fill = if (selected) colors.tabBackgroundSelected else colors.tabBackground
    val border = if (selected) colors.tabIndicator else colors.tabBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(fill)
            .drawBehind { drawRect(color = border, style = Stroke(1f)) }
            .clickable(onClick = onSelect)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            McText(cpuName(cpu), color = colors.textPrimary.value.toInt(), maxWidth = 90)
            if (job != null) {
                McText(
                    job.what().formatAmount(job.amount(), appeng.api.stacks.AmountFormat.SLOT),
                    color = colors.textSecondary.value.toInt(),
                    maxWidth = 90,
                )
            } else {
                val extras = buildString {
                    append(AeAmountFormat.bytes(cpu.storage()))
                    if (cpu.coProcessors() > 0) append(" +").append(cpu.coProcessors())
                    when (cpu.mode()) {
                        CpuSelectionMode.PLAYER_ONLY -> append(" P")
                        CpuSelectionMode.MACHINE_ONLY -> append(" A")
                        else -> Unit
                    }
                }
                McText(extras, color = colors.textSecondary.value.toInt(), maxWidth = 90)
            }
        }
        if (job != null) {
            ItemSlot(
                stack = job.what().wrapForDisplayOrFilter(),
                consumeClicks = false,
                showTooltip = true,
                colors = colors,
            )
        }
    }
    if (job != null) {
        McProgressBar(progress = cpu.progress(), modifier = Modifier.fillMaxWidth().height(2.dp), colors = colors)
    } else {
        Spacer(Modifier.height(0.dp))
    }
}

private fun cpuName(cpu: CraftingStatusMenu.CraftingCpuListEntry): Component =
    cpu.name() ?: GuiText.CPUs.text().copy().append(String.format(" #%d", cpu.serial()))
