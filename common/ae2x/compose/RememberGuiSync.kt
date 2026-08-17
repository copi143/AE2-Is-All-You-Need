package ae2x.compose

import allyouneed.client.compose.platform.rememberFrameCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Reads an AE2 `@GuiSync` (or any menu) value into Compose state. The field is polled once per
 * rendered frame; the returned value only changes — and the caller recomposes — when `!=` says so.
 *
 * ```kotlin
 * val formed = rememberGuiSync { menu.formed }
 * McText(if (formed == 1) "已成形" else "未成形")
 * ```
 */
@Composable
fun <T> rememberGuiSync(read: () -> T): T {
    val latest = rememberUpdatedState(read)
    val state = remember { mutableStateOf(latest.value()) }
    rememberFrameCallback {
        val next = latest.value()
        if (state.value != next) state.value = next
    }
    return state.value
}
