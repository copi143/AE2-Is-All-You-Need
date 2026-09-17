package ae2x.compose.screen

import androidx.compose.ui.graphics.toArgb
import ae2x.compose.LocalAeHost
import ae2x.compose.aePanelBounds
import ae2x.compose.rememberGuiSync
import ae2x.compose.slot.AePlayerInventory
import ae2x.compose.slot.AeRepoGrid
import ae2x.compose.slotsOf
import ae2x.compose.widget.AeLeftBar
import ae2x.compose.widget.AeSearchBar
import ae2x.compose.widget.AeSettingToggle
import ae2x.compose.widget.AeSortToggle
import ae2x.compose.widget.AeUpgradePanel
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import appeng.api.config.Settings
import appeng.api.config.ViewItems
import appeng.api.util.IConfigurableObject
import appeng.core.AEConfig
import appeng.menu.SlotSemantics
import minecraftx.compose.material.McPanel
import minecraftx.compose.material.McScrollbar
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme

@Composable
fun AeTerminalScaffold(
    screen: AeComposeMEScreen<*>,
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = 195.dp,
    height: Dp = 222.dp,
    rows: Int = 6,
    extraLeftBar: @Composable () -> Unit = {},
    extraContent: @Composable () -> Unit = {},
) {
    SideEffect {
        screen.visibleRows = rows
        screen.repo.setRowSize(screen.columns)
        screen.ensureRepoSlots()
        val max = screen.maxScrollRows()
        if (screen.scrollRow > max) screen.scrollRow = max
    }
    val syncedSearch = rememberGuiSync { screen.searchText }
    var search by remember { mutableStateOf(TextFieldValue(syncedSearch)) }
    SideEffect {
        if (syncedSearch != search.text) {
            search = TextFieldValue(syncedSearch, TextRange(syncedSearch.length))
        }
    }
    val viewMode = rememberGuiSync {
        (screen.menu as IConfigurableObject).configManager.getSetting(Settings.VIEW_MODE)
    }
    val externalSearch = AEConfig.instance().isUseExternalSearch
    val configurable = screen.menu as IConfigurableObject
    val menu = LocalAeHost.current.menu
    val hasUpgrades = SlotSemantics.UPGRADE.slotsOf(menu).isNotEmpty()
    val hasViewCells = SlotSemantics.VIEW_CELL.slotsOf(menu).isNotEmpty()
    val maxRows = screen.maxScrollRows()
    val scroll = rememberScrollState()
    scroll.maxScroll = maxRows.toFloat()
    if (!scroll.isAnimating && scroll.display.toInt() != screen.scrollRow) {
        scroll.seek(screen.scrollRow.toFloat())
    }
    val thumb = scroll.display.toInt().coerceIn(0, maxRows)
    if (thumb != screen.scrollRow) screen.scrollRow = thumb
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Top) {
            AeLeftBar {
                AeSortToggle(configurable)
                AeSettingToggle(configurable, Settings.VIEW_MODE)
                if (configurable.configManager.hasSetting(Settings.TYPE_FILTER)) {
                    AeSettingToggle(configurable, Settings.TYPE_FILTER)
                }
                extraLeftBar()
                if (hasUpgrades) AeUpgradePanel()
                if (hasViewCells) {
                    ae2x.compose.slot.AeSlotGrid(SlotSemantics.VIEW_CELL, columns = 1)
                }
            }
            McPanel(width = width, height = height, modifier = modifier.aePanelBounds()) {
                Column(Modifier.padding(7.dp)) {
                    Row {
                        McText(title, color = McTheme.colors.textPrimary.toArgb())
                        Spacer(Modifier.weight(1f))
                        if (!externalSearch) {
                            AeSearchBar(
                                value = search,
                                onValueChange = {
                                    search = it
                                    screen.setSearch(it.text)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        AeRepoGrid(
                            repo = screen.repo,
                            rows = rows,
                            columns = screen.columns,
                            viewOnlyCraftable = viewMode == ViewItems.CRAFTABLE,
                            onEntryClick = screen::handleRepoClick,
                        )
                        if (maxRows > 0) {
                            McScrollbar(
                                state = scroll,
                                modifier = Modifier.size(4.dp, (McTheme.shapes.slotSize * rows)),
                            )
                        }
                    }
                    extraContent()
                    Spacer(Modifier.height(6.dp))
                    AePlayerInventory()
                }
            }
        }
    }
}

private operator fun androidx.compose.ui.unit.Dp.times(count: Int) = this * count.toFloat()
