package allyouneed.client.compose.platform

import allyouneed.client.compose.ui.draw.McDrawScope
import allyouneed.client.compose.ui.layout.Constraints
import allyouneed.client.compose.ui.modifier.ClickableModifier
import allyouneed.client.compose.ui.modifier.ScrollModifier
import allyouneed.client.compose.ui.modifier.foldElements
import allyouneed.client.compose.ui.node.LayoutNode
import allyouneed.client.compose.ui.node.UiApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen

class ComposeOwner(private val screen: Screen) {
    private val rootNode = LayoutNode()
    private val applier = UiApplier(rootNode)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val recomposer = Recomposer(effectCoroutineContext = scope.coroutineContext)
    private var composition: Composition? = null

    fun setContent(content: @Composable () -> Unit) {
        composition = Composition(applier, recomposer).apply { setContent(content) }
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        SnapshotSync.requestApply()

        val layoutConstraints = Constraints(maxWidth = screen.width, maxHeight = screen.height)
        rootNode.remeasure(layoutConstraints)

        val ds = McDrawScope(graphics)
        rootNode.draw(ds)
    }

    fun onMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return false
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        return hitTest(rootNode, mx, my, 0, 0) { it is ClickableModifier && it.enabled }
    }

    fun onMouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        var handled = false
        hitTest(rootNode, mx, my, 0, 0) { mod ->
            if (mod is ScrollModifier) {
                mod.scrollState.scroll(delta.toInt())
                handled = true
            }
            false
        }
        return handled
    }

    fun onMouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = false

    private fun hitTest(
        node: LayoutNode,
        mx: Int, my: Int,
        offsetX: Int, offsetY: Int,
        onFound: (Any) -> Boolean,
    ): Boolean {
        val absX = offsetX + node.x
        val absY = offsetY + node.y
        val absW = node.width
        val absH = node.height

        for (child in node.children.reversed()) {
            if (hitTest(child, mx, my, absX, absY, onFound)) return true
        }

        if (mx in absX until (absX + absW) && my in absY until (absY + absH)) {
            val modifiers = mutableListOf<Any>()
            node.modifier.foldElements {
                if (it is ClickableModifier || it is ScrollModifier) modifiers.add(it)
            }
            for (mod in modifiers) {
                if (onFound(mod)) return true
            }
        }
        return false
    }

    fun dispose() {
        composition?.dispose()
        recomposer.cancel()
        scope.cancel()
    }
}

internal object SnapshotSync {
    fun requestApply() {
        Snapshot.sendApplyNotifications()
    }
}
