package allyouneed.cell

import appeng.api.config.FuzzyMode
import appeng.api.config.IncludeExclude
import appeng.api.upgrades.IUpgradeInventory
import appeng.core.definitions.AEItems
import appeng.core.localization.GuiText
import appeng.util.ConfigInventory
import appeng.util.prioritylist.IPartitionList
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.ItemStack

internal const val TAG_FUZZY = "FuzzyMode"

fun ItemStack.getFuzzyMode(): FuzzyMode {
    val fz = this.orCreateTag.getString(TAG_FUZZY)
    if (fz.isEmpty()) return FuzzyMode.IGNORE_ALL
    return try {
        FuzzyMode.valueOf(fz)
    } catch (_: IllegalArgumentException) {
        FuzzyMode.IGNORE_ALL
    }
}

fun ItemStack.setFuzzyMode(fzMode: FuzzyMode) {
    this.orCreateTag.putString(TAG_FUZZY, fzMode.name)
}

fun buildPartitionList(
    stack: ItemStack,
    upgrades: IUpgradeInventory,
    config: ConfigInventory,
): Pair<IPartitionList, IncludeExclude> {
    val builder = IPartitionList.builder()
    val isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD)
    if (isFuzzy) {
        builder.fuzzyMode(stack.getFuzzyMode())
    }
    builder.addAll(config.keySet())
    val hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD)
    val mode = if (hasInverter) IncludeExclude.BLACKLIST else IncludeExclude.WHITELIST
    return builder.build() to mode
}

fun MutableComponent.appendPartitionInfo(
    mode: IncludeExclude,
    isFuzzy: Boolean,
): MutableComponent {
    val modeText = if (mode == IncludeExclude.BLACKLIST) {
        GuiText.Excluded.text()
    } else {
        GuiText.Included.text()
    }
    var result = this.append(modeText)
    if (isFuzzy) {
        result = result.append(" ").append(GuiText.Fuzzy.text())
    }
    return result
}
