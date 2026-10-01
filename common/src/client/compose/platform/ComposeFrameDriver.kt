package allyouneed.client.compose.platform

import net.minecraft.client.Minecraft
import java.util.concurrent.Executors

/**
 * Drives the update + record phase of every active [ComposeOwner]. Two selectable modes:
 *
 *  - [UpdateMode.GUI_STAGE]: nothing happens here; [ComposeOwner.render]'s fallback path performs
 *    update + record synchronously during the GUI stage, then replays immediately.
 *  - [UpdateMode.PARALLEL]: at the head of every game frame (world-render stage, injected by
 *    `GameRendererMixin`) each owner's update + record is submitted to a dedicated worker thread
 *    so it overlaps world rendering; the GUI stage joins the worker and replays the recorded
 *    commands. If world rendering finishes first, the GUI stage waits for the worker.
 *
 * Switch with [updateMode]. All entry points run on the render thread except the submitted
 * update tasks, which run on the UI worker thread and hold [ComposeOwner.updateLock] — every
 * render-thread entry point into an owner (input, resize, dispose) takes the same lock.
 */
object ComposeFrameDriver {

    enum class UpdateMode(val label: String) {
        GUI_STAGE("同步更新"),
        PARALLEL("并行更新"),
    }

    @Volatile
    var updateMode: UpdateMode = UpdateMode.PARALLEL

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "compose-ui-worker").apply { isDaemon = true }.also { workerThreadRef = it }
    }

    @Volatile
    private var workerThreadRef: Thread? = null

    /** True on the dedicated UI worker thread (parallel mode update/record runs there). */
    internal fun isUiThread(): Boolean = Thread.currentThread() === workerThreadRef

    private val owners = ArrayList<ComposeOwner>()

    /** Monotonic frame counter, bumped once per game frame; owners track it to avoid double work. */
    var frameCounter = 0L
        private set

    internal fun register(owner: ComposeOwner) {
        if (owner !in owners) owners += owner
    }

    internal fun unregister(owner: ComposeOwner) {
        owners -= owner
    }

    /** Called from the GameRenderer mixin at the head of every frame render. */
    fun onGameFrameStart(partialTick: Float) {
        frameCounter++
        val frame = frameCounter
        if (owners.isEmpty()) return
        if (updateMode != UpdateMode.PARALLEL) return
        val mc = Minecraft.getInstance()
        val window = mc.window
        val mouseX = (mc.mouseHandler.xpos() * window.guiScaledWidth / window.screenWidth).toInt()
        val mouseY = (mc.mouseHandler.ypos() * window.guiScaledHeight / window.screenHeight).toInt()
        for (owner in owners.toList()) {
            owner.pendingFrame = executor.submit {
                runCatching { owner.updateAndRecord(frame, mouseX, mouseY, partialTick) }
                    .onFailure { it.printStackTrace() }
            }
        }
    }
}
