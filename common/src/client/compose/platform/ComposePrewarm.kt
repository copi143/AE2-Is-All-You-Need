package allyouneed.client.compose.platform

import androidx.compose.ui.unit.IntSize
import net.minecraft.client.Minecraft

/**
 * Warms up the Compose engine on a background thread during client startup: constructing a throwaway
 * [ComposeOwner] and running one empty composition pays the one-time cost (compose-runtime class
 * loading, [androidx.compose.runtime.Recomposer] cold start, theme/config load) while the game is
 * still loading, so the first real screen no longer stutters on open. Disposal is dispatched back
 * onto the game thread because [ComposeOwner.dispose] touches GLFW cursor state.
 */
object ComposePrewarm {

    @Volatile
    private var started = false

    fun startAsync() {
        if (started) return
        started = true
        Thread({
            runCatching {
                val owner = ComposeOwner { IntSize(16, 16) }
                owner.setContent { }
                Minecraft.getInstance().execute { runCatching { owner.dispose() } }
            }
        }, "compose-prewarm").apply {
            isDaemon = true
            priority = (Thread.NORM_PRIORITY - 2).coerceAtLeast(Thread.MIN_PRIORITY)
        }.start()
    }
}
