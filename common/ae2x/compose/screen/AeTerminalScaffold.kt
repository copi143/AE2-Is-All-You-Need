package ae2x.compose.screen

import ae2x.compose.LocalAeHost
import ae2x.compose.aePanelBounds
import ae2x.compose.rememberGuiSync
import ae2x.compose.slot.AePlayerInventory
import ae2x.compose.slot.AeRepoGrid
import ae2x.compose.widget.AeLeftBar
import ae2x.compose.widget.AeSearchBar
import ae2x.compose.widget.AeSettingToggle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import appeng.api.util.IConfigurableObject
import minecraftx.compose.material.McCarriedStack
import minecraftx.compose.material.McPanel
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
    val host = LocalAeHost.current
    val syncedSearch = rememberGuiSync { screen.searchText }
    var search by remember { mutableStateOf(TextFieldValue(syncedSearch)) }
    if (syncedSearch != search.text) {
        search = TextFieldValue(syncedSearch, TextRange(syncedSearch.length))
    }
    val configurable = screen.menu as IConfigurableObject
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Top) {
            AeLeftBar {
                AeSettingToggle(configurable, Settings.SORT_BY)
                AeSettingToggle(configurable, Settings.SORT_DIRECTION)
                AeSettingToggle(configurable, Settings.VIEW_MODE)
                if (configurable.configManager.hasSetting(Settings.TYPE_FILTER)) {
                    AeSettingToggle(configurable, Settings.TYPE_FILTER)
                }
                extraLeftBar()
            }
            McPanel(width = width, height = height, modifier = modifier.aePanelBounds()) {
                Column(Modifier.padding(7.dp)) {
                    Row {
                        McText(title, color = McTheme.colors.textPrimary.value.toInt())
                        Spacer(Modifier.weight(1f))
                        AeSearchBar(
                            value = search,
                            onValueChange = {
                                search = it
                                screen.setSearch(it.text)
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    AeRepoGrid(
                        repo = screen.repo,
                        rows = rows,
                        columns = screen.columns,
                        onEntryClick = screen::handleRepoClick,
                    )
                    extraContent()
                    Spacer(Modifier.height(6.dp))
                    AePlayerInventory()
                }
            }
        }
        McCarriedStack(host.menu.carried)
    }
}
