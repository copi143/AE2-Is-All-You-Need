package ae2x.compose.widget

import ae2x.compose.rememberGuiSync
import ae2x.compose.slot.AeMenuSlot
import ae2x.compose.slot.AeSlotGrid
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import appeng.core.localization.GuiText
import appeng.menu.SlotSemantics
import appeng.menu.me.items.PatternEncodingTermMenu
import appeng.parts.encoding.EncodingMode
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.material.McButton
import minecraftx.compose.material.McCheckbox
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.StonecutterRecipe

@Composable
fun AeEncodingPanel(
    menu: PatternEncodingTermMenu,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    val mode = rememberGuiSync { menu.mode }
    Column(modifier) {
        AeEncodingTabs(
            values = EncodingMode.entries.toList(),
            selected = mode,
            onSelect = menu::setMode,
            label = { it.encodingLabel() },
        )
        Spacer(Modifier.height(4.dp))
        when (mode) {
            EncodingMode.CRAFTING -> AeCraftingEncoding(menu, colors = colors)
            EncodingMode.PROCESSING -> AeProcessingEncoding(menu, colors = colors)
            EncodingMode.SMITHING_TABLE -> AeSmithingEncoding(menu, colors = colors)
            EncodingMode.STONECUTTING -> AeStonecuttingEncoding(menu, colors = colors)
        }
        Spacer(Modifier.height(4.dp))
        McButton("Encode", onClick = menu::encode, colors = colors)
    }
}

@Composable
fun AeCraftingEncoding(
    menu: PatternEncodingTermMenu,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    val substitute = rememberGuiSync { menu.substitute }
    val substituteFluids = rememberGuiSync { menu.substituteFluids }
    Column(modifier) {
        Row {
            AeSlotGrid(SlotSemantics.CRAFTING_GRID, columns = 3)
            Spacer(Modifier.width(8.dp))
            AeSlotGrid(SlotSemantics.CRAFTING_RESULT, columns = 1)
        }
        Spacer(Modifier.height(4.dp))
        Row {
            McCheckbox(substitute, menu::setSubstitute, label = "Sub", colors = colors)
            Spacer(Modifier.width(8.dp))
            McCheckbox(substituteFluids, menu::setSubstituteFluids, label = "Fluid", colors = colors)
            Spacer(Modifier.width(8.dp))
            McButton("Clear", onClick = menu::clear, colors = colors)
        }
    }
}

@Composable
fun AeProcessingEncoding(
    menu: PatternEncodingTermMenu,
    modifier: Modifier = Modifier,
    visibleRows: Int = 3,
    colors: McColorScheme = McTheme.colors,
) {
    val inputs = menu.processingInputSlots
    val outputs = menu.processingOutputSlots
    val inputRows = if (inputs.isEmpty()) 0 else (inputs.size + 2) / 3
    val maxScroll = (inputRows - visibleRows).coerceAtLeast(0)
    var scrollRow by remember { mutableIntStateOf(0) }
    val row = scrollRow.coerceIn(0, maxScroll)
    val visibleInputs = inputs.drop(row * 3).take(visibleRows * 3).toList()
    val visibleOutputs = outputs.drop(row).take(visibleRows).toList()
    Column(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            AeSlotGrid(visibleInputs, columns = 3)
            Spacer(Modifier.width(8.dp))
            AeSlotGrid(visibleOutputs, columns = 1)
            if (maxScroll > 0) {
                Spacer(Modifier.width(4.dp))
                Column {
                    McButton("▲", onClick = { scrollRow = (row - 1).coerceAtLeast(0) }, colors = colors)
                    McText("${row + 1}/${maxScroll + 1}", color = colors.textSecondary.value.toInt())
                    McButton("▼", onClick = { scrollRow = (row + 1).coerceAtMost(maxScroll) }, colors = colors)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            McButton("Clear", onClick = menu::clear, colors = colors)
            if (menu.canCycleProcessingOutputs()) {
                Spacer(Modifier.width(6.dp))
                McButton("Cycle", onClick = menu::cycleProcessingOutput, colors = colors)
            }
        }
    }
}

@Composable
fun AeSmithingEncoding(
    menu: PatternEncodingTermMenu,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    val substitute = rememberGuiSync { menu.substitute }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AeMenuSlot(menu.smithingTableTemplateSlot)
            Spacer(Modifier.width(2.dp))
            AeMenuSlot(menu.smithingTableBaseSlot)
            Spacer(Modifier.width(2.dp))
            AeMenuSlot(menu.smithingTableAdditionSlot)
            Spacer(Modifier.width(8.dp))
            AeSlotGrid(SlotSemantics.SMITHING_TABLE_RESULT, columns = 1)
        }
        Spacer(Modifier.height(4.dp))
        Row {
            McCheckbox(substitute, menu::setSubstitute, label = "Sub", colors = colors)
            Spacer(Modifier.width(8.dp))
            McButton("Clear", onClick = menu::clear, colors = colors)
        }
    }
}

@Composable
fun AeStonecuttingEncoding(
    menu: PatternEncodingTermMenu,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    val recipes = rememberGuiSync { menu.stonecuttingRecipes }
    val selectedId = rememberGuiSync { menu.stonecuttingRecipeId }
    val access = Minecraft.getInstance().level?.registryAccess()
    Column(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            AeSlotGrid(SlotSemantics.STONECUTTING_INPUT, columns = 1)
            Spacer(Modifier.width(8.dp))
            AeStonecuttingRecipeGrid(
                recipes = recipes,
                selectedId = selectedId,
                resultOf = { recipe ->
                    if (access == null) ItemStack.EMPTY else recipe.getResultItem(access)
                },
                onSelect = { menu.setStonecuttingRecipeId(it.id) },
                colors = colors,
            )
        }
    }
}

@Composable
private fun AeStonecuttingRecipeGrid(
    recipes: List<StonecutterRecipe>,
    selectedId: ResourceLocation?,
    resultOf: (StonecutterRecipe) -> ItemStack,
    onSelect: (StonecutterRecipe) -> Unit,
    colors: McColorScheme,
    columns: Int = 4,
    visibleRows: Int = 3,
) {
    var scrollRow by remember { mutableIntStateOf(0) }
    val totalRows = if (columns <= 0) 0 else (recipes.size + columns - 1) / columns
    val maxScroll = (totalRows - visibleRows).coerceAtLeast(0)
    val row = scrollRow.coerceIn(0, maxScroll)
    val start = row * columns
    val visible = recipes.drop(start).take(visibleRows * columns)
    Column {
        visible.chunked(columns).forEach { line ->
            Row {
                line.forEach { recipe ->
                    val selected = recipe.id == selectedId
                    val fill = if (selected) colors.tabBackgroundSelected else colors.slotBackground
                    val border = if (selected) colors.tabIndicator else colors.slotBorder
                    ItemSlot(
                        stack = resultOf(recipe),
                        modifier = Modifier
                            .background(fill)
                            .drawBehind { drawRect(color = border, style = Stroke(1f)) }
                            .clickable { onSelect(recipe) },
                        consumeClicks = true,
                        interactive = false,
                        colors = colors,
                    )
                }
            }
        }
        if (maxScroll > 0) {
            Spacer(Modifier.height(2.dp))
            Row {
                McButton("▲", onClick = { scrollRow = (row - 1).coerceAtLeast(0) }, colors = colors)
                McText(" ${row + 1}/${maxScroll + 1} ", color = colors.textSecondary.value.toInt())
                McButton("▼", onClick = { scrollRow = (row + 1).coerceAtMost(maxScroll) }, colors = colors)
            }
        }
    }
}

private fun EncodingMode.encodingLabel(): String = when (this) {
    EncodingMode.CRAFTING -> GuiText.CraftingPattern.text().string
    EncodingMode.PROCESSING -> GuiText.ProcessingPattern.text().string
    EncodingMode.SMITHING_TABLE -> GuiText.SmithingTablePattern.text().string
    EncodingMode.STONECUTTING -> GuiText.StonecuttingPattern.text().string
}
