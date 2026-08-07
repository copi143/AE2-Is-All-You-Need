package allyouneed.client.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    // 简化版：展示型输入框，暂未接入 MC 的 EditBox 输入。
    Box(
        modifier = modifier
            .background(Color(0xFF222222))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = 0xFF888888.toInt())
        } else {
            Text(value)
        }
    }
}
