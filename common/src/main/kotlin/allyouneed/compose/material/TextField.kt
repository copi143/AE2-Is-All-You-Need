package allyouneed.compose.material

import allyouneed.compose.ui.layout.*
import allyouneed.compose.ui.modifier.Modifier
import allyouneed.compose.ui.modifier.background
import allyouneed.compose.ui.modifier.padding
import androidx.compose.runtime.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    // 简化版：用 MC 的 EditBox 包装
    // 这里我们只提供占位，真正集成 EditBox 需要在 ComposeOwner 中管理 widget
    Box(
        modifier = modifier
            .background(0xFF222222.toInt())
            .padding(horizontal = 4, vertical = 2),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = 0xFF888888.toInt())
        } else {
            Text(value)
        }
    }
}
