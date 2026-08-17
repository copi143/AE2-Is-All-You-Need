package minecraftx.compose.demo

import minecraftx.compose.material.ItemSlot
import minecraftx.compose.material.McTextField
import minecraftx.compose.foundation.McLine
import minecraftx.compose.foundation.McScrollBox
import minecraftx.compose.material.McScrollbar
import minecraftx.compose.material.McText
import minecraftx.compose.material.McTooltip
import minecraftx.compose.foundation.McVirtualColumn
import minecraftx.compose.material.Text
import allyouneed.client.compose.platform.ComposeContainerScreen
import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import minecraftx.compose.material.McButton
import minecraftx.compose.material.McCheckbox
import minecraftx.compose.material.McNumberField
import minecraftx.compose.material.McProgressBar
import minecraftx.compose.material.McSearchField
import minecraftx.compose.material.McTab
import minecraftx.compose.material.McTabRow
import minecraftx.compose.material.McToggle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import minecraftx.compose.theme.McTheme
import minecraftx.compose.theme.McThemeId
import minecraftx.compose.theme.McThemeSettings
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * 全屏 Compose 演示屏。基于 [ComposeContainerScreen]:EMI 把容器屏当作"当前处理屏",
 * 从屏内点击 ItemSlot 打开 EMI 配方后按 ESC 会回到本屏,而不是回落到 vanilla 物品栏。
 * 空菜单仅用于让 EMI 识别本屏为容器屏;没有槽位,EMI 的配方填充会回落到玩家物品栏。
 */
class ComposeDemoScreen : ComposeContainerScreen<ComposeContainerScreen.EmptyMenu>(
    ComposeContainerScreen.EmptyMenu(),
    ComposeContainerScreen.playerInventory(),
    Component.literal("Compose Demo"),
) {

    @Composable
    override fun Content() {
        var count by remember { mutableStateOf(0) }
        var sliderValue by remember { mutableStateOf(0.5f) }
        var visible by remember { mutableStateOf(true) }
        var highlight by remember { mutableStateOf(false) }
        var tooltipTarget by remember { mutableStateOf<IntOffset?>(null) }
        val alpha by animateFloatAsState(if (highlight) 1f else 0.3f, label = "demoAlpha")
        val mouse = LocalMousePosition.current

        Box(Modifier.fillMaxSize()) {
            // 整个 demo 页作为 McScrollBox 的 flow 内容:内容超屏时整页滚动+裁剪,
            // 不再溢出叠到屏幕底部;窗口缩放时 autoScroll 自动钳回滚动偏移。
            McScrollBox(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
            Text("Compose Demo", color = 0xFFFFAA00.toInt())
            Text("Ctrl+滚轮缩放 UI,当前 %.1fx".format(currentUiScale()), color = 0xFF88FFFF.toInt())

            Spacer(Modifier.fillMaxWidth().padding(vertical = 4.dp))

            Row {
                McButton(
                    if (McThemeSettings.id == McThemeId.Dark) "切到亮色主题" else "切到暗色主题",
                    onClick = { McThemeSettings.toggle() },
                )
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 4.dp))

            Row {
                Text("Count: $count", color = 0xFF00FF00.toInt())
                Spacer(Modifier.size(8.dp))
                McButton("+1", onClick = { count++ }, modifier = Modifier.padding(horizontal = 4.dp))
                McButton("Reset", onClick = { count = 0 }, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            Text("McProgressBar: ${(sliderValue * 100).toInt()}%", color = 0xFFAAAAAA.toInt())
            McProgressBar(progress = sliderValue, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Row {
                McButton("-", onClick = { sliderValue = (sliderValue - 0.1f).coerceIn(0f, 1f) })
                Spacer(Modifier.size(4.dp))
                McButton("+", onClick = { sliderValue = (sliderValue + 0.1f).coerceIn(0f, 1f) })
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Official animation: animated alpha via graphicsLayer
            Text("Animated alpha (graphicsLayer):", color = 0xFFCCCCCC.toInt())
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(Color(0xFF00AAFF)),
                )
                Spacer(Modifier.size(8.dp))
                McButton(if (highlight) "Dim" else "Bright", onClick = { highlight = !highlight })
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Official AnimatedVisibility (fade + scale)
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                McButton(if (visible) "Hide" else "Show", onClick = { visible = !visible })
                Spacer(Modifier.size(8.dp))
                AnimatedVisibility(visible = visible) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color(0xFFFF8800)),
                    )
                }
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Color boxes demo
            Text("Color Boxes:", color = 0xFFCCCCCC.toInt())
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFFFF0000))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFF00FF00))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFF0000FF))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 2.dp)
                        .background(Color(0xFFFFFF00))
                )
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Framework scrollable panel:虚拟化文本列 + 平滑滚动 + 点击/拖拽滚动条
            Text("McVirtualColumn (framework):", color = 0xFFCCCCCC.toInt())
            val demoLines = remember {
                buildList {
                    for (i in 0 until 60) {
                        add(McLine(Component.literal("第 %d 行 - 虚拟化滚动测试文本".format(i)), 4, i * 10))
                    }
                }
            }
            val demoScroll = rememberScrollState().also { it.maxScroll = 480f }
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(276.dp, 120.dp),
            ) {
                Box(Modifier.matchParentSize().background(Color(0x66000000)))
                McVirtualColumn(
                    lines = demoLines,
                    state = demoScroll,
                    modifier = Modifier.matchParentSize(),
                    viewportWidth = 276,
                    viewportHeight = 120,
                    lineHeight = 10,
                )
                McScrollbar(
                    state = demoScroll,
                    modifier = Modifier.align(Alignment.CenterEnd).size(4.dp, 120.dp),
                )
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Framework McScrollBox:通用 overflow 容器,内容可滚动 + 裁剪 + 滚动条
            Text("McScrollBox (framework):", color = 0xFFCCCCCC.toInt())
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(276.dp, 100.dp),
            ) {
                McScrollBox(
                    contentWidth = 276,
                    contentHeight = 220,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    for (i in 0 until 11) {
                        Box(
                            Modifier
                                .offset(0.dp, (i * 20).dp)
                                .size(272.dp, 18.dp)
                                .background(if (i % 2 == 0) Color(0x33333333) else Color(0x22444444))
                                .padding(horizontal = 6.dp),
                        ) {
                            McText(Component.literal("滚动行 %d - 底部超出视口会被裁剪".format(i)), color = 0xFFDDDDDD.toInt())
                        }
                    }
                }
            }

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            Text("McTab / McCheckbox / McToggle / McNumberField / McSearchField:", color = 0xFFCCCCCC.toInt())
            var tab by remember { mutableStateOf(0) }
            McTabRow(Modifier.padding(vertical = 4.dp)) {
                McTab("Crafting", selected = tab == 0, onClick = { tab = 0 })
                McTab("Processing", selected = tab == 1, onClick = { tab = 1 })
                McTab("Machine", selected = tab == 2, onClick = { tab = 2 })
            }
            var checked by remember { mutableStateOf(true) }
            var toggled by remember { mutableStateOf(false) }
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                McCheckbox(checked = checked, onCheckedChange = { checked = it }, label = "enabled")
                Spacer(Modifier.size(8.dp))
                McToggle(checked = toggled, onCheckedChange = { toggled = it })
                Spacer(Modifier.size(8.dp))
                var number by remember { mutableStateOf(64L) }
                McNumberField(value = number, onValueChange = { number = it }, min = 0, max = 1000)
            }
            var search by remember { mutableStateOf(TextFieldValue("")) }
            McSearchField(
                value = search,
                onValueChange = { search = it },
                placeholder = "搜索（右键清空）",
                width = 276,
            )

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // McTextField 输入框演示:像浏览器一样支持"开启输入法"与"关闭输入法(纯 ASCII)"两种模式。
            // IME 模式文本走 Screen.charTyped(直接按键与 IME 提交文本都会到达);ASCII 模式忽略
            // charTyped,按键用 US 布局 shift 表映射。编辑键(退格/方向键/Home/End/Ctrl+A)两模式通用。
            Text("McTextField (IME on/off):", color = 0xFFCCCCCC.toInt())
            var imeText by remember { mutableStateOf(TextFieldValue("")) }
            McTextField(
                value = imeText,
                onValueChange = { imeText = it },
                imeEnabled = true,
                width = 276,
                modifier = Modifier.padding(vertical = 2.dp),
                placeholder = "输入法模式:点我,IME 可直接输中文",
            )
            var asciiText by remember { mutableStateOf(TextFieldValue("")) }
            McTextField(
                value = asciiText,
                onValueChange = { asciiText = it },
                imeEnabled = false,
                width = 276,
                modifier = Modifier.padding(vertical = 2.dp),
                placeholder = "纯 ASCII 模式:忽略输入法(Shift 映射符号)",
            )

            Spacer(Modifier.fillMaxWidth().padding(vertical = 8.dp))

            // Tooltip 双版本:vanilla 浮动 tooltip(ItemSlot)与 Compose 浮动 tooltip(McTooltip)
            Text("Tooltip 双版本:", color = 0xFFCCCCCC.toInt())
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                ItemSlot(
                    stack = ItemStack(Items.GOLD_INGOT),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("左:vanilla 渲染 (ItemSlot)", color = 0xFFAAAAAA.toInt())
            }
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .size(200.dp, 20.dp)
                        .background(Color(0xFF3A3A3A))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Enter -> tooltipTarget = mouse.position
                                        PointerEventType.Move -> tooltipTarget = mouse.position
                                        PointerEventType.Exit -> tooltipTarget = null
                                        else -> Unit
                                    }
                                }
                            }
                        },
                )
                Spacer(Modifier.size(8.dp))
                Text("右:compose 渲染 (McTooltip)", color = 0xFFAAAAAA.toInt())
            }
            }
            }

            if (tooltipTarget != null) {
                McTooltip(
                    lines = listOf(
                        Component.literal("Compose 渲染 tooltip"),
                        Component.literal("随鼠标浮动移动"),
                    ),
                    modifier = Modifier.offset(
                        (tooltipTarget!!.x + 12).dp,
                        (tooltipTarget!!.y + 8).dp,
                    ),
                )
            }
        }
    }
}
