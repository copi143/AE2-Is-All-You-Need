package allyouneed.compose.ui.node

import androidx.compose.runtime.AbstractApplier

class UiApplier(root: LayoutNode) : AbstractApplier<LayoutNode>(root) {
    private val stack = mutableListOf(root)

    override fun insertTopDown(index: Int, instance: LayoutNode) {
        current.children.add(index, instance)
        instance.parent = current
    }

    override fun insertBottomUp(index: Int, instance: LayoutNode) {
        current.children.add(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        repeat(count) {
            current.children.removeAt(index)
        }
    }

    override fun move(from: Int, to: Int, count: Int) {
        if (from == to) return
        val items = (0 until count).map { current.children.removeAt(from) }
        current.children.addAll(to, items)
    }

    override fun onClear() {
        root.children.clear()
        stack.clear()
        stack.add(root)
    }

    override fun onEndChanges() {
        stack.clear()
        stack.add(root)
    }
}
