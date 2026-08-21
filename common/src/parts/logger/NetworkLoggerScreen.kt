package allyouneed.parts.logger

import ae2x.compose.AeComposeScreen
import ae2x.compose.aePanelBounds
import ae2x.compose.rememberGuiSync
import allyouneed.client.compose.platform.rememberScrollState
import allyouneed.util.MODID
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import minecraftx.compose.foundation.McLine
import minecraftx.compose.foundation.McVirtualColumn
import minecraftx.compose.material.McButton
import minecraftx.compose.material.McPanel
import minecraftx.compose.material.McTab
import minecraftx.compose.material.McTabRow
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class NetworkLoggerScreen(
    menu: NetworkLoggerMenu,
    playerInventory: Inventory,
    title: Component,
) : AeComposeScreen<NetworkLoggerMenu>(menu, playerInventory, title) {

    @Composable
    override fun Content() {
        val conflict = rememberGuiSync { menu.conflict }
        val online = rememberGuiSync { menu.online }
        val total = rememberGuiSync { menu.total }
        val filter = rememberGuiSync { menu.filter }
        val offset = rememberGuiSync { menu.offset }
        val page = rememberGuiSync { menu.page }
        val dump = rememberGuiSync { menu.dump }
        val scroll = rememberScrollState()
        val snapped = remember { mutableStateOf(false) }
        val lastDumpSeq = remember { mutableStateOf(0) }
        val lineHeight = 10
        val viewportW = 244
        val viewportH = 132
        val infoColor = McTheme.colors.textPrimary.value.toInt()
        val lines = page.entries.mapIndexed { index, entry ->
            McLine(
                text = entry.toComponent(),
                x = 2,
                y = index * lineHeight,
                color = colorOf(entry.kind.level, infoColor),
            )
        }
        scroll.maxScroll = (lines.size * lineHeight - viewportH).coerceAtLeast(0).toFloat()
        if (!snapped.value && page.entries.isNotEmpty()) {
            scroll.seek(scroll.maxScroll)
            snapped.value = true
        }
        if (dump.seq != 0 && dump.seq != lastDumpSeq.value) {
            lastDumpSeq.value = dump.seq
            saveDump(dump)
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            McPanel(width = 280.dp, height = 200.dp, modifier = Modifier.aePanelBounds()) {
                Column(Modifier.padding(8.dp)) {
                    McText(Component.translatable("gui.$MODID.log.title"), color = McTheme.colors.textPrimary.value.toInt())
                    Spacer(Modifier.height(4.dp))
                    McText(
                        statusText(conflict, online, total),
                        color = statusColor(conflict, online, McTheme.colors.textSecondary.value.toInt()),
                    )
                    if (conflict == 1) {
                        Spacer(Modifier.height(2.dp))
                        McText(
                            Component.translatable("gui.$MODID.log.conflict_banner"),
                            color = COLOR_ERROR,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    McTabRow(Modifier.fillMaxWidth()) {
                        for (category in NetworkLogCategory.entries) {
                            McTab(
                                label = Component.translatable("gui.$MODID.log.${category.langKey}").string,
                                selected = filter and category.mask != 0,
                                onClick = { menu.toggleCategory(category) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    McVirtualColumn(
                        lines = lines,
                        state = scroll,
                        modifier = Modifier.fillMaxWidth().height(viewportH.dp),
                        viewportWidth = viewportW,
                        viewportHeight = viewportH,
                        lineHeight = lineHeight,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        McButton("<", onClick = { menu.olderPage() }, enabled = offset > 0)
                        McText(
                            Component.translatable("gui.$MODID.log.page", offset + 1, (offset + page.entries.size).coerceAtLeast(offset), total),
                            color = McTheme.colors.textSecondary.value.toInt(),
                        )
                        McButton(">", onClick = { menu.newerPage() }, enabled = offset + LogStore.PAGE_SIZE < total)
                        McButton(
                            Component.translatable("gui.$MODID.log.download").string,
                            onClick = { menu.requestDownload() },
                        )
                        McButton(
                            Component.translatable("gui.$MODID.log.clear").string,
                            onClick = { menu.clearLogs() },
                        )
                    }
                }
            }
        }
    }

    private fun statusText(conflict: Int, online: Int, total: Int): Component {
        val key = when {
            conflict == 1 -> "gui.$MODID.log.status.conflict"
            online == 1 -> "gui.$MODID.log.status.online"
            else -> "gui.$MODID.log.status.offline"
        }
        return Component.translatable(key, total)
    }

    private fun statusColor(conflict: Int, online: Int, secondary: Int): Int = when {
        conflict == 1 -> COLOR_ERROR
        online == 1 -> secondary
        else -> COLOR_WARN
    }

    private fun saveDump(dump: NetworkLogDump) {
        val mc = Minecraft.getInstance()
        val dir = mc.gameDirectory.toPath().resolve("ae2isallyouneed").resolve("network-logs")
        try {
            java.nio.file.Files.createDirectories(dir)
            val stamp = java.time.LocalDateTime.now().format(FILE_STAMP)
            val id = if (dump.loggerId == 0) "unbound" else "%06x".format(dump.loggerId)
            val path = dir.resolve("network-logger-$id-$stamp.log")
            val text = buildString {
                for (entry in dump.entries) {
                    append(entry.toPlainLine())
                    append('\n')
                }
            }
            java.nio.file.Files.writeString(path, text)
            mc.player?.displayClientMessage(
                Component.translatable("gui.$MODID.log.downloaded", path.toAbsolutePath().toString()),
                false,
            )
        } catch (e: Exception) {
            mc.player?.displayClientMessage(
                Component.translatable("gui.$MODID.log.download_failed", e.message ?: e.javaClass.simpleName),
                false,
            )
        }
    }

    private fun colorOf(level: NetworkLogLevel, info: Int): Int = when (level) {
        NetworkLogLevel.INFO -> info
        NetworkLogLevel.WARN -> COLOR_WARN
        NetworkLogLevel.ERROR -> COLOR_ERROR
    }

    companion object {
        private const val COLOR_WARN = 0xFFCC66
        private const val COLOR_ERROR = 0xFF5555
        private val FILE_STAMP: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
